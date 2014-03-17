Oskari.clazz.define("Oskari.digiroad2.bundle.assetlayer.AssetLayer", function() {
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
                "src" : "../../../../bundles/digiroad2/bundle/assetlayer/domain/BusStopLayer.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/assetlayer/domain/BusStopLayerModelBuilder.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/assetlayer/AssetLayer.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/assetlayer/template/Templates.js"
            }]
        },
        "bundle" : {
            "manifest" : {
                "Bundle-Identifier" : "assetlayer",
                "Bundle-Name" : "assetlayer",
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
                        "Name" : "AssetLayer",
                        "Title" : "AssetLayer"
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
Oskari.bundle_manager.installBundleClass("assetlayer", "Oskari.digiroad2.bundle.assetlayer.AssetLayer");
