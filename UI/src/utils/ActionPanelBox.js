window.AssetActionPanel = function(identifier, header, isExpanded, validityPeriods, hasEditButton) {
    var readOnly = false;

    _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
    };

    var actionPanel =jQuery(
        '<div class="actionPanel ' + identifier + '">' +
            '<div class="layerGroup">' +
                '<div class="layerGroupImg_'+identifier+' layerGroupImg_unselected_'+identifier+'"></div>' +
                '<div class="layerGroupLabel">'+header+'</div>' +
            '</div>' +
        '</div>');

    var validityPeriodContainer = '<div class="layerGroupLayers"></div>';

    var layerGroup = actionPanel.find('.layerGroup');

    var actionButtons = jQuery(
        '<div class="actionButtons">' +
            '<div data-action="Select" class="actionButton actionButtonActive actionPanelButtonSelect">' +
            '<div class="actionPanelButtonSelectImage actionPanelButtonSelectActiveImage"></div>' +
            '</div>' +
            '<div data-action="Add" class="actionButton actionPanelButtonAdd">' +
            '<div class="actionPanelButtonAddImage"></div>' +
            '</div>' +
            '</div>');

    var cursor = {'Select' : 'default', 'Add' : 'crosshair', 'Remove' : 'no-drop'};

    var render =  function() {
        renderView();
        bindEvents();
    };

    var layerFunc = (function() {
        var selectedValidityPeriods = [];

        var handleAssetModified = function(asset) {
            var layerToChange =  _.find(selectedValidityPeriods, function(x) { return asset.validityPeriod === x.id; });
            if(!!layerToChange) {
                layerToChange.selected = true;
                layerToChange.dom.find('input').prop('checked', true);
                triggerValidityPeriodsToBus();
            }
        };

        var triggerValidityPeriodsToBus = function() {
            var selectedLayers = _.filter(selectedValidityPeriods, function(x) { return x.selected; });
            eventbus.trigger('validityPeriod:changed', _.pluck(selectedLayers, 'id'));
        };

        var prepareLayerSelection = function(layer) {
            var mapBusStopLayer = _.template(
                '<div class="busStopLayer">' +
                    '<div class="busStopLayerCheckbox"><input type="checkbox" {{selected}} /></div>' +
                    '{{name}}' +
                '</div>');
            var layerSelection = $(mapBusStopLayer({ selected: layer.selected ? "checked" : "", name: layer.label }));
            var periodSelection = {id: layer.id, selected: layer.selected, dom: layerSelection };
            selectedValidityPeriods.push(periodSelection);
            layerSelection.find(':input').change(function (target) {
                periodSelection.selected = target.currentTarget.checked;
                triggerValidityPeriodsToBus();
            });
            return layerSelection;
        };

        return {
            prepareLayer: prepareLayerSelection,
            handleAssetModified: handleAssetModified
        };
    })();

    var button = $('<button class="readOnlyMode actionModeButton"></button>');
    var editButtonForGroup = function() {
        var editMode = function () {
            readOnly = false;
            showEditMode();
            button.off().click(readMode);
        };
        var readMode = function () {
            readOnly = true;
            eventbus.trigger('application:readOnly', readOnly);
            changeTool('Select');

            button.removeClass('editMode').addClass('readOnlyMode').text('Siirry muokkaustilaan');

            actionButtons.hide();
            layerGroup.removeClass('layerGroupEditMode');
            button.off().click(editMode);
        };

        return button.text('Siirry muokkaustilaan').click(editMode);
    };

    var renderView = function() {
        jQuery(".panelLayerGroup").append(actionPanel);

        if(validityPeriods.length > 0) {
            actionPanel.append(validityPeriodContainer);
            _.forEach(validityPeriods, function (layer) {
                actionPanel.find(".layerGroupLayers").append(layerFunc.prepareLayer(layer));
            });
        }

        if(isExpanded){
            layerGroup.addClass('layerGroupSelectedMode');
            actionPanel.find('.layerGroupImg_unselected_' + identifier).addClass('layerGroupImg_selected_' + identifier);
        } else {
            layerGroup.on('click', handleLayerGrpClick);
        }
    };

    var bindEvents =  function() {
        actionButtons.find(".actionButton").on("click", function() {
            var data = jQuery(this);
            var action = data.attr('data-action');
            changeTool(action);
        });
    };

    var changeTool = function(action) {
        // TODO: scoping!
        jQuery(".actionButtonActive").removeClass("actionButtonActive");
        jQuery(".actionPanelButton"+action).addClass("actionButtonActive");
        jQuery(".actionPanelButtonSelectActiveImage").removeClass("actionPanelButtonSelectActiveImage");
        jQuery(".actionPanelButtonAddActiveImage").removeClass("actionPanelButtonAddActiveImage");
        jQuery(".olMap").css('cursor', cursor[action]);
        eventbus.trigger('tool:changed', action);
    };

    var showEditMode = function() {
        eventbus.trigger('application:readOnly', readOnly);
        changeTool('Select');

        actionButtons.show();
        button.removeClass('readOnlyMode').addClass('editMode').text('Siirry katselutilaan');

        layerGroup.addClass('layerGroupSelectedMode');
        layerGroup.addClass('layerGroupEditMode');
    };

    var handleLayerGrpClick = function(){
        eventbus.trigger('layer:selected', identifier);
    };

    var collapse = function () {
        eventbus.trigger('application:readOnly', readOnly);
        changeTool('Select');

        button.removeClass('readOnlyMode').addClass('editMode').text('Siirry muokkaustilaan');

        actionButtons.hide();
        layerGroup.removeClass('layerGroupSelectedMode');
        layerGroup.removeClass('layerGroupEditMode');
        layerGroup.on('click', handleLayerGrpClick);
    };

    var expand = function() {
        eventbus.trigger('application:readOnly', readOnly);
        changeTool('Select');
        button.removeClass('editMode').addClass('readOnlyMode').text('Siirry muokkaustilaan');
        layerGroup.addClass('layerGroupSelectedMode');
        layerGroup.removeClass('layerGroupEditMode');
        layerGroup.off();
    };

    var handleGroupSelect = function(id) {
        if(identifier === id) {
            actionPanel.find('.layerGroupLayers').show();
            actionPanel.find('.layerGroupImg_unselected_'+id).addClass('layerGroupImg_selected_'+id);
            layerGroup.addClass('layerGroupSelectedMode');
            button.show();
            expand();
        } else {
            readOnly = true;
            actionPanel.find('.layerGroupLayers').hide();
            actionPanel.find('.layerGroupImg_selected_'+identifier).removeClass('layerGroupImg_selected_'+identifier);
            button.hide();
            collapse();
        }
    };

    var handleRoles = function(roles) {
        if(_.contains(roles, "viewer") === false){
            actionPanel.append(actionButtons.hide());
            if(hasEditButton) actionPanel.append(editButtonForGroup());
        }
    };

    eventbus.on('roles:fetched', handleRoles);
    eventbus.on('asset:fetched assetPropertyValue:fetched asset:created', layerFunc.handleAssetModified, this);
    eventbus.on('layer:selected', handleGroupSelect, this);
    render();
};