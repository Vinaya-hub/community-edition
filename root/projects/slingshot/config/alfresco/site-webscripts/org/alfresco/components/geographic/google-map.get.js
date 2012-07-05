<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">
<import resource="classpath:/alfresco/site-webscripts/org/alfresco/components/documentlibrary/include/documentlist.lib.js">

function main()
{
   AlfrescoUtil.param('nodeRef');
   AlfrescoUtil.param('site', null);
   AlfrescoUtil.param('container', 'documentLibrary');

   var documentDetails = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site);
   if (documentDetails)
   {
      var properties = documentDetails.item.node.properties,
         eT = properties["exif:exposureTime"];
      if (eT)
      {
         if (parseInt(eT * 1000, 10) > 0)
         {
            properties["exif:exposureTime"] = "1/" + Math.ceil(1/eT);
         }
      }
      model.document = documentDetails.item;
      model.documentDetailsJSON = jsonUtils.toJSONString(documentDetails);
      doclibCommon();
   }
}

main();

// Widget instantiation metadata...
model.webScriptWidgets = [];
var googleMap = {};
googleMap.name = "Alfresco.component.GoogleMap";
googleMap.provideMessages = true;
googleMap.provideOptions = true;
googleMap.options = {};
googleMap.options.documentDetails = model.documentDetailsJSON;
model.webScriptWidgets.push(googleMap);
