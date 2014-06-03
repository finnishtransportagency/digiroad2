Oskari.clazz.define('Oskari.digiroad2.bundle.map.template.Templates',
    function () {
        _.templateSettings = {
            interpolate: /\{\{(.+?)\}\}/g
        };
        var styleMap = new OpenLayers.StyleMap({
            "select": new OpenLayers.Style({
                strokeWidth: 6,
                strokeOpacity: 1,
                strokeColor: "#5eaedf"
            }),
            "default": new OpenLayers.Style({
                strokeWidth: 5,
                strokeColor: "#ff55dd",
                strokeOpacity: 0.8
            })
        });
        var roadLinkTypeStyleLookup = {
            PrivateRoad: { strokeColor: "#00ccdd" },
            Street: { strokeColor: "#11bb00" },
            Road: { strokeColor: "#ff0000" }
        };
        styleMap.addUniqueValueRules("default", "type", roadLinkTypeStyleLookup);
        this.roadStyles = styleMap;
        styleMap.styles.default.rules.push(new OpenLayers.Rule({
            elseFilter: true,
            symbolizer: styleMap.styles.default.defaultStyle
        }));
    }
);