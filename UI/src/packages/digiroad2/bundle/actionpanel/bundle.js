/**
 * @class Oskari.digiroad2.bundle.actionpanel.ActionPanel
 */
Oskari.clazz.define("Oskari.digiroad2.bundle.actionpanel.ActionPanel", function() {
    }, {
        /*
         * implementation for protocol 'Oskari.bundle.Bundle'
         */
        "create" : function() {
            var me = this;
            var inst =
                Oskari.clazz.create("Oskari.digiroad2.bundle.actionpanel.ActionPanel");
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
                "src" : "../../../../bundles/digiroad2/bundle/actionpanel/ActionPanel.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/actionpanel/ActionPanelBox.js"
            },{
                "type" : "text/javascript",
                "src" : "../../../../bundles/digiroad2/bundle/actionpanel/template/Templates.js"
            }],
            "locales" : []
        },
        "bundle" : {
            "manifest" : {
                "Bundle-Identifier" : "actionpanel",
                "Bundle-Name" : "actionpanel",
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
                        "Name" : "ActionPanel",
                        "Title" : "ActionPanel"
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
Oskari.bundle_manager.installBundleClass("actionpanel", "Oskari.digiroad2.bundle.actionpanel.ActionPanel");