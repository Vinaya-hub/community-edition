/*
 * Copyright (C) 2005-2011 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.repo.jscript.app;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tag property decorator class.
 *
 * @author Mike Hatfield
 */
public class TagPropertyDecorator implements JSONPropertyDecorator
{
    private static Log logger = LogFactory.getLog(TagPropertyDecorator.class);

    private ServiceRegistry services;
    private NodeService nodeService = null;

    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.services = serviceRegistry;
        this.nodeService = serviceRegistry.getNodeService();
    }

    public Serializable decorate(NodeRef nodeRef, String propertyName, Serializable value)
    {
        Collection<NodeRef> collection = (Collection<NodeRef>)value;
        Object[] array = new Object[collection.size()];
        int index = 0;

        for (NodeRef obj : collection)
        {
            try
            {
                Map<String, Serializable> jsonObj = new LinkedHashMap<String, Serializable>(2);
                jsonObj.put("name", this.nodeService.getProperty(obj, ContentModel.PROP_NAME));
                jsonObj.put("nodeRef", obj.toString());
                array[index++] = jsonObj;
            }
            catch (InvalidNodeRefException e)
            {
                logger.warn("Tag with nodeRef " + obj.toString() + " does not exist.");
            }
        }

        return array;
    }
}
