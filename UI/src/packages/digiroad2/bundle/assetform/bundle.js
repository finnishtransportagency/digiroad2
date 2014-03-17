Oskari.clazz.define("Oskari.digiroad2.bundle.assetform.AssetForm", function() {
    }, {
        /*
         * implementation for protocol 'Oskari.bundle.Bundle'
         */
        "create" : function() {
            var me = this;
            var inst =
                Oskari.clazz.create("Oskari.digiroad2.bundle.assetform.AssetForm");
            return inst;
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
                "src" : "../../../../bundles/digiroad2/bundle/assetform/AssetForm.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/assetform/template/Templates.js"
            },{
                "type" : "text/css",
                "src" : "../../../../resources/digiroad2/bundle/assetform/css/style.css"
            }],
            "locales" : []
        },
        "bundle" : {
            "manifest" : {
                "Bundle-Identifier" : "assetform",
                "Bundle-Name" : "assetform",
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
                        "Name" : "AssetForm",
                        "Title" : "AssetForm"
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
Oskari.bundle_manager.installBundleClass("assetform", "Oskari.digiroad2.bundle.assetform.AssetForm");