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
                strokeColor: "#a4a4a2",
                strokeOpacity: 0.8
            })
        });
        var roadLinkTypeStyleLookup = {
            privateRoad: { strokeColor: "#00ccdd" },
            street: { strokeColor: "#ff55dd" },
            road: { strokeColor: "#11bb00" }
        };
        styleMap.addUniqueValueRules("default", "type", roadLinkTypeStyleLookup);
        this.roadStyles = styleMap;
        styleMap.styles.default.rules.push(new OpenLayers.Rule({
            elseFilter: true,
            symbolizer: styleMap.styles.default.defaultStyle
        }));
    }
);