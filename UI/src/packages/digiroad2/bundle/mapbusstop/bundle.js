/**
 * @class Oskari.digiroad2.bundle.busstop.BusStopBundle
 */
Oskari.clazz.define("Oskari.digiroad2.bundle.busstop.BusStopBundle", function() {
    }, {
        /*
         * implementation for protocol 'Oskari.bundle.Bundle'
         */
        "create" : function() {

            return null;
        },
        "update" : function(manager, bundle, bi, info) {
            manager.alert("RECEIVED update notification " + info);
        }
    },

    /**
     * metadata
     */
    {

        "protocol" : ["Oskari.bundle.Bundle", "Oskari.mapframework.bundle.extension.ExtensionBundle"],
        "source" : {
            "scripts" : [{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/mapbusstop/domain/BusStopLayer.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/mapbusstop/domain/BusStopLayerModelBuilder.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/mapbusstop/plugin/BusStopLayerPlugin.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/mapbusstop/plugin/template/Templates.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/common/event/ApplicationInitializedEvent.js"
            }],
            "locales" : [{
                "lang" : "fi",
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/mapbusstop/locale/fi.js"
            }]
        },
        "bundle" : {
            "manifest" : {
                "Bundle-Identifier" : "busstop",
                "Bundle-Name" : "busstop",
                "Bundle-Tag" : {
                    "mapframework" : true
                },

                "Bundle-Icon" : {
                    "href" : "icon.png"
                },
                "Bundle-Author" : [{
                    "Name" : "jjk",
                    "Organisation" : "livi.fi",
                    "Temporal" : {
                        "Start" : "2013",
                        "End" : "2017"
                    },
                    "Copyleft" : {
                        "License" : {
                            "License-Name" : "EUPL",
                            "License-Online-Resource" : "http://www.paikkatietoikkuna.fi/license"
                        }
                    }
                }],
                "Bundle-Name-Locale" : {
                    "fi" : {
                        "Name" : "BusStop",
                        "Title" : "BusStop"
                    },
                    "en" : {}
                },
                "Bundle-Version" : "1.0.0",
                "Import-Namespace" : ["Oskari", "Ext"]
            }
        }
    });

/**
 * Install this bundle
 */
Oskari.bundle_manager.installBundleClass("mapbusstop", "Oskari.digiroad2.bundle.busstop.BusStopBundle");
