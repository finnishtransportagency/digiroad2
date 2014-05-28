Oskari.clazz.define('Oskari.digiroad2.bundle.map.template.Templates',
    function () {
        _.templateSettings = {
            interpolate: /\{\{(.+?)\}\}/g
        };
        this.roadStyles = new OpenLayers.StyleMap({
            "select": new OpenLayers.Style(null, {
                rules: [
                    new OpenLayers.Rule({
                        symbolizer: {
                            "Line": {
                                strokeWidth: 6,
                                strokeOpacity: 1,
                                strokeColor: "#5eaedf"
                            }
                        }
                    })
                ]
            }),
            "default": new OpenLayers.Style(null, {
                rules: [
                    new OpenLayers.Rule({
                        symbolizer: {
                            "Line": {
                                strokeWidth: 5,
                                strokeOpacity: 1,
                                strokeColor: "#a4a4a2"
                            }
                        }
                    })
                ]
            })
        });

    }
);