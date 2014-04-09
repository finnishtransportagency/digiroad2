window.AssetActionPanel = function(identifier, header, icon) {

    var layerGroup =jQuery(
        '<div class="actionPanel ' + identifier + '">' +
            '<div class="layerGroup">' +
                '<div class="layerGroupImg_'+identifier+' layerGroupImg_unselected_'+identifier+'"></div>' +
                '<div class="layerGroupLabel">'+header+'</div>' +
            '</div>' +
            '<div class="layerGroupLayers"></div>' +
        '</div>');

    var mapBusStopLayer = _.template(
        '<div class="busStopLayer">' +
            '<div class="busStopLayerCheckbox"><input class="layerSelector_'+identifier+'" type="checkbox" {{selected}} data-validity-period="{{id}}"/></div>' +
            '<div class="busStopLayerName">{{name}}</div>' +
            '</div>');

    var actionButtons = jQuery(
        '<div class="actionButtons readOnlyModeHidden">' +
            '<div data-action="Select" class="actionButton actionButtonActive actionPanelButtonSelect">' +
            '<div class="actionPanelButtonSelectImage actionPanelButtonSelectActiveImage"></div>' +
            '</div>' +
            '<div data-action="Add" class="actionButton actionPanelButtonAdd">' +
            '<div class="actionPanelButtonAddImage"></div>' +
            '</div>' +
            '</div>');

    var editButton = jQuery('<button class="editMode actionModeButton">Muokkaa</button>');
    var readyButton = jQuery('<button class="readOnlyMode actionModeButton editModeHidden">Siirry katselutilaan</button>');
    var editMessage = '<div class="editMessage readOnlyModeHidden">Olet muokkaustilassa</div>';

    var cursor = {'Select' : 'default', 'Add' : 'crosshair', 'Remove' : 'no-drop'};

    var layerPeriods = [
        {id: "current", label: "Voimassaolevat", selected: true},
        {id: "future", label: "Tulevat"},
        {id: "past", label: "Käytöstä poistuneet"}
    ];

    var handleAssetModified = function(asset) {
        var $el = jQuery('input.layerSelector_'+identifier+'[data-validity-period=' + asset.validityPeriod + ']');
        if (!$el.is(':checked')) {
            $el.click();
        }
    };

    var render =  function() {
        renderView();
        bindEvents();
    };

    var renderView =  function() {
        jQuery(".panelLayerGroup").append(layerGroup);
        var $el = jQuery.find('.'+identifier);

        _.forEach(layerPeriods, function (layer) {
            layerGroup.find(".layerGroupLayers").append(mapBusStopLayer({ selected: layer.selected ? "checked" : "", id:layer.id, name: layer.label}));
        });

        layerGroup.append(actionButtons);
        layerGroup.append(editButton);
        layerGroup.append(readyButton);
        jQuery(".container").append(editMessage);
    };

    var bindEvents =  function() {
        jQuery(".panelControl").on("click", function() {
            togglePanel();
        });

        jQuery(".layerSelector_"+identifier).on("change", function() {
            var selectedValidityPeriods = $('input.layerSelector_'+identifier).filter(function(_, v) {
                return $(v).is(':checked');
            }).map(function(_, v) {
                return $(v).attr('data-validity-period');
            }).toArray();
            eventbus.trigger('validityPeriod:changed', selectedValidityPeriods);
        });

        jQuery(".actionButton").on("click", function() {
            var data = jQuery(this);
            var action = data.attr('data-action');
            changeTool(action);
        });

        editButton.on('click', function() {
            toggleEditMode(false);
            editButton.hide();
            readyButton.show();
            actionButtons.show();
        });

        readyButton.on('click', function() {
            toggleEditMode(true);
            readyButton.hide();
            editButton.show();
            actionButtons.hide();
        });

        layerGroup.find('.layerGroup').on('click', function() {
            eventbus.trigger('layer:selected',identifier);
        });
    };

    var changeTool = function(action) {
        jQuery(".actionButtonActive").removeClass("actionButtonActive");
        jQuery(".actionPanelButton"+action).addClass("actionButtonActive");
        jQuery(".actionPanelButtonSelectActiveImage").removeClass("actionPanelButtonSelectActiveImage");
        jQuery(".actionPanelButtonAddActiveImage").removeClass("actionPanelButtonAddActiveImage");
        jQuery(".olMap").css('cursor', cursor[action]);
        eventbus.trigger('tool:changed', action);
    };

    var toggleEditMode = function(readOnly) {
            changeTool('Select');
            eventbus.trigger('asset:unselected');
            eventbus.trigger('application:readOnly', readOnly);
            jQuery('.editMessage').toggleClass('readOnlyModeHidden');
            layerGroup.find('.layerGroup').toggleClass('layerGroupSelectedMode');
            layerGroup.find('.layerGroup').toggleClass('layerGroupEditMode');

        };

    var togglePanel = function() {
        jQuery(".actionPanel").toggleClass('actionPanelClosed');
    };

    var hideEditMode = function() {
        readyButton.hide();
        actionButtons.hide();
        layerGroup.find('.layerGroup').removeClass('layerGroupSelectedMode');
        layerGroup.find('.layerGroup').removeClass('layerGroupEditMode');
        jQuery('.editMessage').addClass('readOnlyModeHidden');
    };

    var handleGroupSelect = function(id) {
        if (identifier === id) {
            layerGroup.find('.layerGroupLayers').removeClass('readOnlyModeHidden');
            layerGroup.find('.layerGroupImg_unselected_'+id).addClass('layerGroupImg_selected_'+id);
            editButton.show();
            hideEditMode();
            layerGroup.find('.layerGroup').addClass('layerGroupSelectedMode');
        } else {
            layerGroup.find('.layerGroupLayers').addClass('readOnlyModeHidden');
            layerGroup.find('.layerGroupImg_selected_'+identifier).removeClass('layerGroupImg_selected_'+identifier);
            editButton.hide();
            hideEditMode();
        }
    };

    eventbus.on('asset:fetched assetPropertyValue:fetched asset:created', handleAssetModified, this);
    eventbus.on('layer:selected', handleGroupSelect, this);

    render();
};