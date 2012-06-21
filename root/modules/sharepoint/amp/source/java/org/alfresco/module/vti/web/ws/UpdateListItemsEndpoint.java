/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.alfresco.module.vti.web.ws;

import org.alfresco.module.vti.handler.ListServiceHandler;
import org.alfresco.module.vti.metadata.dic.VtiError;
import org.alfresco.module.vti.metadata.model.ListInfoBean;
import org.alfresco.repo.site.SiteDoesNotExistException;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.jaxen.SimpleNamespaceContext;

/**
 * Class for handling the UpdateListItems soap method, which
 *  is how entries are added/updated/deleted in a List.
 * 
 * @author Nick Burch
 */
public class UpdateListItemsEndpoint extends AbstractListEndpoint
{
    @SuppressWarnings("unused")
    private final static Log logger = LogFactory.getLog(UpdateListItemsEndpoint.class);

    /**
     * constructor
     *
     * @param handler
     */
    public UpdateListItemsEndpoint(ListServiceHandler handler)
    {
        super(handler);
    }
    
    /**
     * Fetches all the details of the update, and processes
     */
    @Override
    protected void executeListActionDetails(VtiSoapRequest soapRequest, VtiSoapResponse soapResponse, String siteName,
            String listName, Element requestElement, SimpleNamespaceContext nc) throws Exception
    {
        // Have the List Fetched
        ListInfoBean list = null;
        try
        {
           list = handler.getList(listName, listName);
        }
        catch(SiteDoesNotExistException se)
        {
           // The specification defines the exact code that must be
           //  returned in case of a file not being found
           long code = VtiError.V_LIST_NOT_FOUND.getErrorCode();
           String message = "Site not found: " + se.getMessage();
           throw new VtiSoapException(message, code, se);
        }
        catch(FileNotFoundException fnfe)
        {
           // The specification defines the exact code that must be
           //  returned in case of a file not being found
           long code = VtiError.V_LIST_NOT_FOUND.getErrorCode();
           String message = "List not found: " + fnfe.getMessage();
           throw new VtiSoapException(message, code, fnfe);
        }
        
        // Fetch the update details
        // TODO
    }

    /**
     * Not used, we are too specific
     */
    @Override
    protected ListInfoBean executeListAction(VtiSoapRequest soapRequest,
         String dws, String listName, String description, int templateID) throws Exception 
    {
        throw new IllegalStateException("Should not be called, UpdateListItems has special handling");
    }
}
