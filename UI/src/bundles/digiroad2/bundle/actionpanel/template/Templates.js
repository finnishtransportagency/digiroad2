Oskari.clazz.define('Oskari.digiroad2.bundle.actionpanel.template.Templates',
    function () {
        _.templateSettings = {
            interpolate: /\{\{(.+?)\}\}/g
        };
        this.panelControl =
            '<div class="panelControl">' +
                '<div class="panelControlLine"></div>' +
                '<div class="panelControlLine"></div>' +
                '<div class="panelControlLine"></div>' +
            '</div>'+
            '<div class="actionPanel">' +
                '<div class="layerGroup">' +
                    '<div class="layerGroupImg">' +
                        '<img src="src/resources/digiroad2/bundle/actionpanel/images/bussi_valkoinen.png">' +
                    '</div>' +
                        '<div class="layerGroupLabel">Joukkoliikenteen pysäkit</div>' +
                    '</div>' +
                    '<div class="layerGroupLayers">' +
                '</div>' +
            '</div>';

        this.mapBusStopLayer = _.template(
            '<div class="busStopLayer">' +
                '<div class="busStopLayerCheckbox"><input class="layerSelector" type="checkbox" {{selected}} data-validity-period="{{id}}"/></div>' +
                '<div class="busStopLayerName">{{name}}</div>' +
            '</div>');
        this.actionButtons =
            '<div class="actionButtons readOnlyModeHidden">' +
                '<div data-action="Select" class="actionButton actionButtonActive actionPanelButtonSelect">' +
                    '<div class="actionPanelButtonSelectImage actionPanelButtonSelectActiveImage"></div>' +
                '</div>' +
                '<div data-action="Add" class="actionButton actionPanelButtonAdd">' +
                    '<div class="actionPanelButtonAddImage"></div>' +
                '</div>' +
            '</div>';

        this.editButton = '<button class="editMode actionModeButton">Muokkaa</button>';
        this.readyButton = '<button class="readOnlyMode actionModeButton editModeHidden">Valmis</button>';
    }
);