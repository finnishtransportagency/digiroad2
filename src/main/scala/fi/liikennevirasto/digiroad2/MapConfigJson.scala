package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.user.Configuration

object MapConfigJson {
  def mapConfig(userConfig: Configuration): String = {
    val zoom = userConfig.zoom.getOrElse(2)
    val east = userConfig.east.getOrElse(390000)
    val north = userConfig.north.getOrElse(6900000)
    val municipalityNumber = userConfig.municipalityNumber.getOrElse(235)

    s"""{
   "toolbar":{
      "state":{

      },
      "conf":{

      }
   },
   "layerselection2":{
      "state":{

      },
      "conf":{

      }
   },
   "mapfull":{
      "state":{
         "selectedLayers":[
            {
               "id":"base_35"
            }, {
                 "id":"235"
             }
         ],
         "zoom":${zoom},
         "east":"${east}",
         "north":"${north}"
      },
      "conf":{
	     "size": {
			"width": "100%",
			"height": "100%"
		 },
         "globalMapAjaxUrl":"/api/layers?",
         "plugins":[
            {
               "id":"Oskari.mapframework.bundle.mapmodule.plugin.LayersPlugin"
            },
            {
               "id":"Oskari.mapframework.mapmodule.WmsLayerPlugin"
            },
            {
               "id":"Oskari.mapframework.mapmodule.MarkersPlugin"
            },
            {
               "id":"Oskari.mapframework.mapmodule.ControlsPlugin"
            },
            {
               "id":"Oskari.mapframework.mapmodule.GetInfoPlugin"
            },
            {
               "id":"Oskari.mapframework.bundle.mapwfs.plugin.wfslayer.WfsLayerPlugin"
            },
            {
               "id":"Oskari.digiroad2.bundle.mapbusstop.plugin.BusStopLayerPlugin"
            },
            {
               "id":"Oskari.mapframework.wmts.mapmodule.plugin.WmtsLayerPlugin"
            },
            {
               "id":"Oskari.mapframework.bundle.mapmodule.plugin.ScaleBarPlugin"
            },
            {
               "id":"Oskari.mapframework.bundle.mapmodule.plugin.Portti2Zoombar"
            },
            {
               "id":"Oskari.mapframework.bundle.mapmodule.plugin.PanButtons"
            },
            {
              "id": "Oskari.mapframework.bundle.mapmodule.plugin.BackgroundLayerSelectionPlugin",
              "config": {
                "baseLayers": [
                  "base_2",
                  "24",
                  "base_35"
                ],
                "showAsDropdown": false
              }
            }
         ],
         "layers":[
             {
                 "wmsName":"bussit",
                 "type":"busstoplayer",
                 "id":235,
                 "minScale":5000,
                 "wmsUrl":"/data/dummy/busstops.json",
                 "url":"/api/assets?assetTypeId=10&municipalityNumber=${municipalityNumber}",
                 "roadLinesUrl" :"/api/roadlinks?municipalityNumber=${municipalityNumber}",
                 "maxScale":1 ,
                 "orgName":"LiVi",
                 "inspire":"Ominaisuustiedot",
                 "name" : "Voimassaolevat"
             },
            {
               "type":"base",
               "id":"base_35",
               "name":"Taustakartta",
               "subLayer":[
                  {
                     "wmsName":"taustakartta_5k",
                     "type":"wmslayer",
                     "id":184,
                     "minScale":5000,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":1
                  },
                  {
                     "wmsName":"taustakartta_10k",
                     "type":"wmslayer",
                     "id":185,
                     "minScale":25001,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":5001
                  },
                  {
                     "wmsName":"taustakartta_20k",
                     "type":"wmslayer",
                     "id":186,
                     "minScale":40001,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":25000
                  },
                  {
                     "wmsName":"taustakartta_40k",
                     "type":"wmslayer",
                     "id":187,
                     "minScale":2,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":1
                  },
                  {
                     "wmsName":"taustakartta_80k",
                     "type":"wmslayer",
                     "id":188,
                     "minScale":56702,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":40000
                  },
                  {
                     "wmsName":"taustakartta_160k",
                     "type":"wmslayer",
                     "id":189,
                     "minScale":141742,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":56702
                  },
                  {
                     "wmsName":"taustakartta_320k",
                     "type":"wmslayer",
                     "id":190,
                     "minScale":283474,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":141742
                  },
                  {
                     "wmsName":"taustakartta_800k",
                     "type":"wmslayer",
                     "id":191,
                     "minScale":566939,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":283474
                  },
                  {
                     "wmsName":"taustakartta_2m",
                     "type":"wmslayer",
                     "id":192,
                     "minScale":1417333,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":566939
                  },
                  {
                     "wmsName":"taustakartta_4m",
                     "type":"wmslayer",
                     "id":193,
                     "minScale":2834657,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":1417333
                  },
                  {
                     "wmsName":"taustakartta_8m",
                     "type":"wmslayer",
                     "id":194,
                     "minScale":15000000,
                     "wmsUrl":"http://a.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://b.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://c.karttatiili.fi/dataset/taustakarttasarja/service/wms,http://d.karttatiili.fi/dataset/taustakarttasarja/service/wms",
                     "maxScale":2834657
                  }
               ],
               "orgName":"Taustakartta",
               "inspire":"Taustakartta"
             },{
               "dataUrl_uuid": "b20a360b-1734-41e5-a5b8-0e90dd9f2af3",
               "wmsName": "ortokuva",
               "styles": {},
               "descriptionLink": "http://www.paikkatietoikkuna.fi/web/guest/ortokuvat",
               "baseLayerId": 15,
               "orgName": "Maanmittauslaitos",
               "type": "wmslayer",
               "legendImage": "",
               "formats": {},
               "isQueryable": false,
               "id": 24,
               "minScale": 50000,
               "dataUrl": "/catalogue/ui/metadata.html?uuid=b20a360b-1734-41e5-a5b8-0e90dd9f2af3",
               "style": "",
               "wmsUrl": "http://a.karttatiili.fi/dataset/ortoilmakuva/service/wms,http://b.karttatiili.fi/dataset/ortoilmakuva/service/wms,http://c.karttatiili.fi/dataset/ortoilmakuva/service/wms,http://d.karttatiili.fi/dataset/ortoilmakuva/service/wms",
               "updated": "Fri Aug 24 11:53:00 EEST 2012",
               "admin": {},
               "orderNumber": 8,
               "name": "Ortoilmakuva",
               "permissions": {
                  "publish": "no_publication_permission"
               },
               "opacity": 100,
               "subtitle": "",
               "inspire": "Ortoilmakuvat",
               "maxScale": 1
            }
         ],
         "imageLocation":"/oskari.org/resources",
         "user":{
            "lastName":"",
            "nickName":"",
            "userUUID":"",
            "firstName":"",
            "loginName":""
         }
      }
   },
   "personaldata":{
      "state":{

      },
      "conf":{
      }
   },
   "publisher":{
      "state":{

      },
      "conf":{

      }
   },
   "openlayers-default-theme":{
      "state":{

      },
      "conf":{

      }
   },
   "search":{
      "state":{

      },
      "conf":{

      }
   },
   "featuredata":{
      "state":{

      },
      "conf":{

      }
   },
   "divmanazer":{
      "state":{

      },
      "conf":{

      }
   },
   "statehandler":{
      "state":{

      },
      "conf":{

      }
   },
   "infobox":{
      "state":{

      },
      "conf":{

      }
   },
   "coordinatedisplay":{
      "state":{

      },
      "conf":{

      }
   }
}"""
  }
}