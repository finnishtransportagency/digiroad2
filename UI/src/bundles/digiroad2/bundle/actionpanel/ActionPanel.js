Oskari.clazz.define("Oskari.digiroad2.bundle.actionpanel.ActionPanel",
    function() {
        this.started = false;
        this.mediator = null;
        this._cursor = {};
        this._readOnly = true;
    }, {
        getName: function() {
            return 'ActionPanel';
        },
        setSandbox: function() {},
        update: function() {
        },
        start: function() {
            if (this.started) {
                return;
            }
            this.started = true;
            // Should this not come as a param?
            var sandbox = Oskari.$('sandbox');
            sandbox.register(this);
            this.setSandbox(sandbox);
            for(var p in this.eventHandlers) {
                if (p) {
                    sandbox.registerForEventByName(this, p);
                }
            }
            this._render();
        },
        init: function() {
            eventbus.on('asset:fetched assetPropertyValue:fetched asset:created', this._handleAssetModified, this);
            this._templates = Oskari.clazz.create('Oskari.digiroad2.bundle.actionpanel.template.Templates');
            this._cursor = {'Select' : 'default', 'Add' : 'crosshair', 'Remove' : 'no-drop'};
            this._layerPeriods = [
                {id: "current", label: "Voimassaolevat", selected: true},
                {id: "future", label: "Tulevat"},
                {id: "past", label: "Käytöstä poistuneet"}
            ];
        },

        _handleAssetModified: function(asset) {
          var $el = jQuery('input.layerSelector[data-validity-period=' + asset.validityPeriod + ']');
          if (!$el.is(':checked')) {
            $el.click();
          }
        },

        _render: function() {
          this._renderView();
          this._bindEvents();
        },
        _renderView: function() {
            var me = this;
            jQuery("#maptools").html(me._templates.panelControl);
            _.forEach(me._layerPeriods, function (layer) {
                jQuery(".layerGroupLayers").append(me._templates.mapBusStopLayer({ selected: layer.selected ? "checked" : "", id:layer.id, name: layer.label}));
            });
            if (!this._readOnly) {
                jQuery(".actionPanel").append(me._templates.actionButtons);
            } else {
               jQuery(".actionPanel").append('<button class="editMode">Muokkaa</button>').click(function() {
                   eventbus.trigger('asset:unselected');
                   eventbus.trigger('application:readOnly', false);
               });
            }
        },
        _bindEvents: function() {
            var me = this;
            jQuery(".panelControl").on("click", function() {
                me._togglePanel();
            });

            jQuery(".layerSelector").on("change", function() {
                var selectedValidityPeriods = $('input.layerSelector').filter(function(_, v) {
                    return $(v).is(':checked');
                }).map(function(_, v) {
                        return $(v).attr('data-validity-period');
                    }).toArray();
                eventbus.trigger('validityPeriod:changed', selectedValidityPeriods);
            });

            jQuery(".actionButton").on("click", function() {
                var data = jQuery(this);
                var action = data.attr('data-action');
                jQuery(".actionButtonActive").removeClass("actionButtonActive");
                jQuery(".actionPanelButton"+action).addClass("actionButtonActive");
                jQuery(".actionPanelButtonSelectActiveImage").removeClass("actionPanelButtonSelectActiveImage");
                jQuery(".actionPanelButtonAddActiveImage").removeClass("actionPanelButtonAddActiveImage");
                jQuery(".actionPanelButtonRemoveActiveImage").removeClass("actionPanelButtonRemoveActiveImage");
                jQuery(".actionPanelButton"+action+"Image").addClass("actionPanelButton"+action+"ActiveImage");
                jQuery(".olMap").css('cursor', me._cursor[action]);
                eventbus.trigger('tool:changed', action);
            });
        },
        _togglePanel: function() {
            jQuery(".actionPanel").toggleClass('actionPanelClosed');
        },
    }, {
        protocol : ['Oskari.bundle.BundleInstance', 'Oskari.mapframework.module.Module']
    });