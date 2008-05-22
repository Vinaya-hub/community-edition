/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.domain.hibernate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.repo.domain.AccessControlListDAO;
import org.alfresco.repo.domain.DbAccessControlList;
import org.alfresco.repo.security.permissions.ACEType;
import org.alfresco.repo.security.permissions.ACLType;
import org.alfresco.repo.security.permissions.AccessControlEntry;
import org.alfresco.repo.security.permissions.AccessControlList;
import org.alfresco.repo.security.permissions.AccessControlListProperties;
import org.alfresco.repo.security.permissions.NodePermissionEntry;
import org.alfresco.repo.security.permissions.PermissionEntry;
import org.alfresco.repo.security.permissions.PermissionReference;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
import org.alfresco.repo.security.permissions.impl.AclChange;
import org.alfresco.repo.security.permissions.impl.AclDaoComponent;
import org.alfresco.repo.security.permissions.impl.PermissionsDaoComponent;
import org.alfresco.repo.security.permissions.impl.SimpleNodePermissionEntry;
import org.alfresco.repo.security.permissions.impl.SimplePermissionEntry;
import org.alfresco.repo.transaction.TransactionalDao;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.AccessPermission;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.util.GUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Common suppot for permisisons dao
 * 
 * Sub classes deteremine how ACLs are cascaded to children and how changes may COW/version children as ACLs are pushed down.
 * 
 * TODO: remove the protocol to dao mapping
 * 
 * @author andyh
 *
 */
public abstract class AbstractPermissionsDaoComponentImpl implements PermissionsDaoComponent, TransactionalDao
{
    private static Log logger = LogFactory.getLog(AbstractPermissionsDaoComponentImpl.class);

    protected static final boolean INHERIT_PERMISSIONS_DEFAULT = true;

    protected AclDaoComponent aclDaoComponent;

    private Map<String, AccessControlListDAO> fProtocolToACLDAO;

    private AccessControlListDAO fDefaultACLDAO;

    /** a uuid identifying this unique instance */
    private String uuid;

    AbstractPermissionsDaoComponentImpl()
    {
        this.uuid = GUID.generate();
    }

    /**
     * Get the ACL DAO component
     * @return - the acl dao component
     */
    public AclDaoComponent getAclDaoComponent()
    {
        return aclDaoComponent;
    }

    /**
     * Set the ACL DAO component
     * @param aclDaoComponent
     */
    public void setAclDaoComponent(AclDaoComponent aclDaoComponent)
    {
        this.aclDaoComponent = aclDaoComponent;
    }

    /**
     * Checks equality by type and uuid
     */
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        else if (!(obj instanceof AbstractPermissionsDaoComponentImpl))
        {
            return false;
        }
        AbstractPermissionsDaoComponentImpl that = (AbstractPermissionsDaoComponentImpl) obj;
        return this.uuid.equals(that.uuid);
    }

    /**
     * @see #uuid
     */
    public int hashCode()
    {
        return uuid.hashCode();
    }

    /**
     * Does this <tt>Session</tt> contain any changes which must be synchronized with the store?
     * 
     * @return true => changes are pending
     */
    public boolean isDirty()
    {
        return aclDaoComponent.isDirty();
    }

    /**
     * Just flushes the session
     */
    public void flush()
    {
        aclDaoComponent.flush();
    }

    /**
     * NO-OP
     */
    public void beforeCommit()
    {
        aclDaoComponent.beforeCommit();
    }

    /**
     * Set the mapping of protocol to DAO
     * @param map
     */
    public void setProtocolToACLDAO(Map<String, AccessControlListDAO> map)
    {
        fProtocolToACLDAO = map;
    }

    /**
     * Set the default DAO
     * @param defaultACLDAO
     */
    public void setDefaultACLDAO(AccessControlListDAO defaultACLDAO)
    {
        fDefaultACLDAO = defaultACLDAO;
    }

    /**
     * Helper to choose appropriate NodeService for the given NodeRef
     * 
     * @param nodeRef
     *            The NodeRef to dispatch from.
     * @return The appropriate NodeService.
     */
    protected AccessControlListDAO getACLDAO(NodeRef nodeRef)
    {
        AccessControlListDAO ret = fProtocolToACLDAO.get(nodeRef.getStoreRef().getProtocol());
        if (ret == null)
        {
            return fDefaultACLDAO;
        }
        return ret;
    }

    protected DbAccessControlList getAccessControlList(NodeRef nodeRef)
    {
        DbAccessControlList acl = getACLDAO(nodeRef).getAccessControlList(nodeRef);
        return acl;
    }

    protected CreationReport getMutableAccessControlList(NodeRef nodeRef)
    {
        DbAccessControlList acl = getACLDAO(nodeRef).getAccessControlList(nodeRef);
        if (acl == null)
        {
            return createAccessControlList(nodeRef, INHERIT_PERMISSIONS_DEFAULT, null);
        }
        else
        {
            switch (acl.getAclType())
            {
            case FIXED:
            case GLOBAL:
            case SHARED:
            case LAYERED:
                // We can not set an ACL on node that has one of these types so we need to make a new one ....
                return createAccessControlList(nodeRef, INHERIT_PERMISSIONS_DEFAULT, acl);
            case DEFINING:
            case OLD:
            default:
                // Force a copy on write if one is required
                getACLDAO(nodeRef).forceCopy(nodeRef);
                return new CreationReport(acl, Collections.<AclChange> emptyList());
            }
        }
    }

    public NodePermissionEntry getPermissions(NodeRef nodeRef)
    {
        // Create the object if it is not found.
        // Null objects are not cached in hibernate
        // If the object does not exist it will repeatedly query to check its
        // non existence.
        NodePermissionEntry npe = null;
        DbAccessControlList acl = null;
        try
        {
            acl = getAccessControlList(nodeRef);
        }
        catch (InvalidNodeRefException e)
        {
            // Do nothing.
        }

        if (acl == null)
        {
            // there isn't an access control list for the node - spoof a null one
            SimpleNodePermissionEntry snpe = new SimpleNodePermissionEntry(nodeRef, true, Collections.<SimplePermissionEntry> emptyList());
            npe = snpe;
        }
        else
        {
            npe = createSimpleNodePermissionEntry(nodeRef);
        }
        // done
        if (logger.isDebugEnabled())
        {
            logger.debug("Got NodePermissionEntry for node: \n" + "   node: " + nodeRef + "\n" + "   acl: " + npe);
        }
        return npe;
    }

    @SuppressWarnings("unchecked")
    public Map<NodeRef, Set<AccessPermission>> getAllSetPermissions(final String authority)
    {
        throw new UnsupportedOperationException();
    }

    public Set<NodeRef> findNodeByPermission(final String authority, final PermissionReference permission, final boolean allow)
    {
        throw new UnsupportedOperationException();
    }

    // Utility methods to create simple detached objects for the outside world
    // We do not pass out the hibernate objects

    private SimpleNodePermissionEntry createSimpleNodePermissionEntry(NodeRef nodeRef)
    {
        DbAccessControlList acl = getACLDAO(nodeRef).getAccessControlList(nodeRef);
        if (acl == null)
        {
            // there isn't an access control list for the node - spoof a null one
            SimpleNodePermissionEntry snpe = new SimpleNodePermissionEntry(nodeRef, true, Collections.<SimplePermissionEntry> emptyList());
            return snpe;
        }
        else
        {
            AccessControlList info = aclDaoComponent.getAccessControlList(acl.getId());

            SimpleNodePermissionEntry cached = info.getCachedSimpleNodePermissionEntry();
            if(cached != null)
            {
                return cached;
            }
            
            ArrayList<SimplePermissionEntry> spes = new ArrayList<SimplePermissionEntry>(info.getEntries().size());
            for (AccessControlEntry entry : info.getEntries())
            {
                SimplePermissionEntry spe = new SimplePermissionEntry(nodeRef, entry.getPermission(), entry.getAuthority(), entry.getAccessStatus(), entry.getPosition());
                spes.add(spe);
            }
            SimpleNodePermissionEntry snpe = new SimpleNodePermissionEntry(nodeRef, acl.getInherits(), spes);
            
            info.setCachedSimpleNodePermissionEntry(snpe);
            return snpe;
        }
    }
    
    private SimpleNodePermissionEntry createSimpleNodePermissionEntry(StoreRef storeRef)
    {
        DbAccessControlList acl = getACLDAO(storeRef).getAccessControlList(storeRef);
        if (acl == null)
        {
            // there isn't an access control list for the node - spoof a null one
            SimpleNodePermissionEntry snpe = new SimpleNodePermissionEntry(null, true, Collections.<SimplePermissionEntry> emptyList());
            return snpe;
        }
        else
        {
            AccessControlList info = aclDaoComponent.getAccessControlList(acl.getId());

            ArrayList<SimplePermissionEntry> spes = new ArrayList<SimplePermissionEntry>(info.getEntries().size());
            for (AccessControlEntry entry : info.getEntries())
            {
                SimplePermissionEntry spe = new SimplePermissionEntry(null, entry.getPermission(), entry.getAuthority(), entry.getAccessStatus(), entry.getPosition());
                spes.add(spe);
            }
            SimpleNodePermissionEntry snpe = new SimpleNodePermissionEntry(null, acl.getInherits(), spes);
            return snpe;
        }
    }

    public boolean getInheritParentPermissions(NodeRef nodeRef)
    {
        DbAccessControlList acl = null;
        try
        {
            acl = getAccessControlList(nodeRef);
        }
        catch (InvalidNodeRefException e)
        {
            return INHERIT_PERMISSIONS_DEFAULT;
        }
        if (acl == null)
        {
            return INHERIT_PERMISSIONS_DEFAULT;
        }
        else
        {
            return aclDaoComponent.getAccessControlListProperties(acl.getId()).getInherits();
        }
    }

    @SuppressWarnings("unchecked")
    public void deletePermissions(String authority)
    {
        @SuppressWarnings("unused")
        List<AclChange> changes = aclDaoComponent.deleteAccessControlEntries(authority);
        // ignore changes - deleting an authority does not casue all acls to version

    }

    public void deletePermissions(NodeRef nodeRef, final String authority)
    {
        DbAccessControlList acl = null;
        try
        {
            acl = getACLDAO(nodeRef).getAccessControlList(nodeRef);
        }
        catch (InvalidNodeRefException e)
        {
            return;
        }
        switch (acl.getAclType())
        {
        case FIXED:
        case GLOBAL:
        case SHARED:
            throw new IllegalStateException("Can not delete from this acl in a node context " + acl.getAclType());
        case DEFINING:
        case LAYERED:
        case OLD:
        default:
            CreationReport report = getMutableAccessControlList(nodeRef);
            SimpleAccessControlEntry pattern = new SimpleAccessControlEntry();
            pattern.setAuthority(authority);
            pattern.setPosition(Integer.valueOf(0));
            List<AclChange> changes = aclDaoComponent.deleteAccessControlEntries(report.getCreated().getId(), pattern);
            getACLDAO(nodeRef).updateChangedAcls(nodeRef, changes);
            break;
        }
    }

    /**
     * Deletes all permission entries (access control list entries) that match the given criteria. Note that the access
     * control list for the node is not deleted.
     */
    public void deletePermission(NodeRef nodeRef, String authority, PermissionReference permission)
    {
        DbAccessControlList acl = null;
        try
        {
            acl = getACLDAO(nodeRef).getAccessControlList(nodeRef);
        }
        catch (InvalidNodeRefException e)
        {
            return;
        }

        switch (acl.getAclType())
        {
        case FIXED:
        case GLOBAL:
        case SHARED:
            throw new IllegalStateException("Can not delete from this acl in a node context " + acl.getAclType());
        case DEFINING:
        case LAYERED:
        case OLD:
        default:
            CreationReport report = getMutableAccessControlList(nodeRef);
            SimpleAccessControlEntry pattern = new SimpleAccessControlEntry();
            pattern.setAuthority(authority);
            pattern.setPermission(permission);
            pattern.setPosition(Integer.valueOf(0));
            List<AclChange> changes = aclDaoComponent.deleteAccessControlEntries(report.getCreated().getId(), pattern);
            getACLDAO(nodeRef).updateChangedAcls(nodeRef, changes);
            break;
        }

    }

    public void setPermission(NodeRef nodeRef, String authority, PermissionReference permission, boolean allow)
    {
        CreationReport report = null;
        try
        {
            report = getMutableAccessControlList(nodeRef);
        }
        catch (InvalidNodeRefException e)
        {
            return;
        }
        if (report.getCreated() != null)
        {
            SimpleAccessControlEntry entry = new SimpleAccessControlEntry();
            entry.setAuthority(authority);
            entry.setPermission(permission);
            entry.setAccessStatus(allow ? AccessStatus.ALLOWED : AccessStatus.DENIED);
            entry.setAceType(ACEType.ALL);
            entry.setPosition(Integer.valueOf(0));
            List<AclChange> changes = aclDaoComponent.setAccessControlEntry(report.getCreated().getId(), entry);
            List<AclChange> all = new ArrayList<AclChange>(changes.size() + report.getChanges().size());
            all.addAll(report.getChanges());
            all.addAll(changes);
            getACLDAO(nodeRef).updateChangedAcls(nodeRef, all);
        }
    }

    public void setPermission(PermissionEntry permissionEntry)
    {
        setPermission(permissionEntry.getNodeRef(), permissionEntry.getAuthority(), permissionEntry.getPermissionReference(), permissionEntry.isAllowed());
    }

    public void setPermission(NodePermissionEntry nodePermissionEntry)
    {
        NodeRef nodeRef = nodePermissionEntry.getNodeRef();

        // Get the access control list
        // Note the logic here requires to know whether it was created or not
        DbAccessControlList existing = getAccessControlList(nodeRef);
        if (existing != null)
        {
            deletePermissions(nodeRef);
        }
        // create the access control list

        existing = getAccessControlList(nodeRef);
        CreationReport report = createAccessControlList(nodeRef, nodePermissionEntry.inheritPermissions(), existing);

        // add all entries
        for (PermissionEntry pe : nodePermissionEntry.getPermissionEntries())
        {
            SimpleAccessControlEntry entry = new SimpleAccessControlEntry();
            entry.setAuthority(pe.getAuthority());
            entry.setPermission(pe.getPermissionReference());
            entry.setAccessStatus(pe.isAllowed() ? AccessStatus.ALLOWED : AccessStatus.DENIED);
            entry.setAceType(ACEType.ALL);
            entry.setPosition(Integer.valueOf(0));
            List<AclChange> changes = aclDaoComponent.setAccessControlEntry(report.getCreated().getId(), entry);
            List<AclChange> all = new ArrayList<AclChange>(changes.size() + report.getChanges().size());
            all.addAll(report.getChanges());
            all.addAll(changes);
            getACLDAO(nodeRef).updateChangedAcls(nodeRef, all);
        }
    }

    public void setInheritParentPermissions(NodeRef nodeRef, boolean inheritParentPermissions)
    {
        DbAccessControlList acl = getAccessControlList(nodeRef);
        if ((acl == null) && (inheritParentPermissions == INHERIT_PERMISSIONS_DEFAULT))
        {
            return;
        }
        if ((acl != null) && (acl.getInherits() == inheritParentPermissions))
        {
            return;
        }

        CreationReport report = getMutableAccessControlList(nodeRef);

        List<AclChange> changes;
        if (!inheritParentPermissions)
        {
            changes = aclDaoComponent.disableInheritance(report.getCreated().getId(), false);
        }
        else
        {
            // TODO: Find inheritance
            changes = aclDaoComponent.enableInheritance(report.getCreated().getId(), null);
        }
        List<AclChange> all = new ArrayList<AclChange>(changes.size() + report.getChanges().size());
        all.addAll(report.getChanges());
        all.addAll(changes);
        getACLDAO(nodeRef).updateChangedAcls(nodeRef, all);
    }

    
    
    public void deletePermission(StoreRef storeRef, String authority, PermissionReference permission)
    {
        DbAccessControlList acl = getAccessControlList(storeRef);
        if(acl == null)
        {
            return;
        }
        acl = getMutableAccessControlList(storeRef);
        SimpleAccessControlEntry pattern = new SimpleAccessControlEntry();
        pattern.setAuthority(authority);
        pattern.setPermission(permission);
        pattern.setPosition(Integer.valueOf(0));
        aclDaoComponent.deleteAccessControlEntries(acl.getId(), pattern);
    }

    private DbAccessControlList getMutableAccessControlList(StoreRef storeRef)
    {
        DbAccessControlList acl = getACLDAO(storeRef).getAccessControlList(storeRef);
        if(acl == null)
        {
            SimpleAccessControlListProperties properties = new SimpleAccessControlListProperties();
            properties.setAclType(ACLType.DEFINING);
            properties.setVersioned(false);
            properties.setInherits(false);
            // Accept default versioning
            Long id = aclDaoComponent.createAccessControlList(properties);
            acl = aclDaoComponent.getDbAccessControlList(id);
            getACLDAO(storeRef).setAccessControlList(storeRef, acl);
        }
        return acl;
    }

    private AccessControlListDAO getACLDAO(StoreRef storeRef)
    {
        AccessControlListDAO ret = fProtocolToACLDAO.get(storeRef.getProtocol());
        if (ret == null)
        {
            return fDefaultACLDAO;
        }
        return ret;
    }

    private DbAccessControlList getAccessControlList(StoreRef storeRef)
    {
       return getACLDAO(storeRef).getAccessControlList(storeRef);
    }

    public void deletePermissions(StoreRef storeRef, String authority)
    {
        DbAccessControlList acl = getAccessControlList(storeRef);
        if(acl == null)
        {
            return;
        }
        acl = getMutableAccessControlList(storeRef);
        SimpleAccessControlEntry pattern = new SimpleAccessControlEntry();
        pattern.setAuthority(authority);
        pattern.setPosition(Integer.valueOf(0));
        aclDaoComponent.deleteAccessControlEntries(acl.getId(), pattern);
    }

    public void deletePermissions(StoreRef storeRef)
    {
        getACLDAO(storeRef).setAccessControlList(storeRef, null);   
    }

    public void setPermission(StoreRef storeRef, String authority, PermissionReference permission, boolean allow)
    {
        DbAccessControlList acl = getMutableAccessControlList(storeRef);
    
        SimpleAccessControlEntry entry = new SimpleAccessControlEntry();
        entry.setAuthority(authority);
        entry.setPermission(permission);
        entry.setAccessStatus(allow ? AccessStatus.ALLOWED : AccessStatus.DENIED);
        entry.setAceType(ACEType.ALL);
        entry.setPosition(Integer.valueOf(0));
        aclDaoComponent.setAccessControlEntry(acl.getId(), entry); 
    }

    
    
    public NodePermissionEntry getPermissions(StoreRef storeRef)
    {
        // Create the object if it is not found.
        // Null objects are not cached in hibernate
        // If the object does not exist it will repeatedly query to check its
        // non existence.
        NodePermissionEntry npe = null;
        DbAccessControlList acl = null;
        try
        {
            acl = getAccessControlList(storeRef);
        }
        catch (InvalidNodeRefException e)
        {
            // Do nothing.
        }

        if (acl == null)
        {
            // there isn't an access control list for the node - spoof a null one
            SimpleNodePermissionEntry snpe = new SimpleNodePermissionEntry(null, true, Collections.<SimplePermissionEntry> emptyList());
            npe = snpe;
        }
        else
        {
            npe = createSimpleNodePermissionEntry(storeRef);
        }
       
        return npe;
    }

    

    public AccessControlListProperties getAccessControlListProperties(NodeRef nodeRef)
    {
        DbAccessControlList acl = getACLDAO(nodeRef).getAccessControlList(nodeRef);
        if(acl == null)
        {
            return null;
        }
        return aclDaoComponent.getAccessControlListProperties(acl.getId());
    }

    protected abstract CreationReport createAccessControlList(NodeRef nodeRef, boolean inherit, DbAccessControlList existing);

    
    /**
     * Internal class used for reporting the collateral damage when creating an new ACL entry 
     * @author andyh
     *
     */        
    static class CreationReport
    {
        DbAccessControlList created;

        List<AclChange> changes;

        CreationReport(DbAccessControlList created, List<AclChange> changes)
        {
            this.created = created;
            this.changes = changes;
        }

        /**
         * Set the change list
         * 
         * @param changes
         */
        public void setChanges(List<AclChange> changes)
        {
            this.changes = changes;
        }

        /**
         * Set the ACL that was created
         * @param created
         */
        public void setCreated(DbAccessControlList created)
        {
            this.created = created;
        }

        /**
         * Get the change list
         * @return - the change list
         */
        public List<AclChange> getChanges()
        {
            return changes;
        }

        /**
         * Get the created ACL
         * @return - the acl
         */
        public DbAccessControlList getCreated()
        {
            return created;
        }

    }
}
