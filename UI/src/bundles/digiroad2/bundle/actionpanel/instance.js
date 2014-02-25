/**
 * @class Oskari.digiroad2.bundle.actionpanel.ActionPanelBundleInstance
 *
 */
Oskari.clazz.define("Oskari.digiroad2.bundle.actionpanel.ActionPanelBundleInstance",

    /**
     * @method create called automatically on construction
     * @static
     */
        function() {
        this.sandbox = null;
        this.started = false;
        this.mediator = null;
        this._cursor = {};
    }, {
        /**
         * @static
         * @property __name
         */
        __name : 'ActionPanel',

        /**
         * @method getName
         * @return {String} the name for the component
         */
        getName : function() {
            return this.__name;
        },
        /**
         * @method setSandbox
         * @param {Oskari.mapframework.sandbox.Sandbox} sandbox
         * Sets the sandbox reference to this component
         */
        setSandbox : function(sbx) {
            this.sandbox = sbx;
        },
        /**
         * @method getSandbox
         * @return {Oskari.mapframework.sandbox.Sandbox}
         */
        getSandbox : function() {
            return this.sandbox;
        },
        /**
         * @method update
         * implements BundleInstance protocol update method - does nothing atm
         */
        update : function() {
        },
        /**
         * @method start
         * implements BundleInstance protocol start methdod
         */
        start : function() {
            var me = this;
            if(me.started) {
                return;
            }
            me.started = true;
            // Should this not come as a param?
            var sandbox = Oskari.$('sandbox');
            sandbox.register(me);
            me.setSandbox(sandbox);
            for(var p in me.eventHandlers) {
                if(p) {
                    sandbox.registerForEventByName(me, p);
                }
            }
            this._render();
        },
        /**
         * @method init
         * implements Module protocol init method - initializes request handlers
         */
        init : function() {
            var me = this;
            me._templates = Oskari.clazz.create('Oskari.digiroad2.bundle.actionpanel.template.Templates');
            me._cursor = {'Select' : 'default', 'Add' : 'crosshair', 'Remove' : 'no-drop'};
            me._layerPeriods = [
                {id: "current", label: "Voimassaolevat", selected: true},
                {id: "future", label: "Tulevat"},
                {id: "past", label: "Käytöstä poistuneet"}
            ];
            return null;
        },

        eventHandlers: {
          'mapbusstop.AssetModifiedEvent': function(event) {
            this._handleAssetModified(event.getAsset());
          }
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
            jQuery(".actionPanel").append(me._templates.actionButtons);
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
                me._triggerEvent('actionpanel.ValidityPeriodChangedEvent', selectedValidityPeriods);
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
                me._triggerEvent('actionpanel.ActionPanelToolSelectionChangedEvent', action);
            });
        },
        _togglePanel: function() {
            jQuery(".actionPanel").toggleClass('actionPanelClosed');
        },
        _triggerEvent: function(eventName, parameter) {
            var eventBuilder = this.getSandbox().getEventBuilder(eventName);
            var event = eventBuilder(parameter);
            this.getSandbox().notifyAll(event);
        },
        /**
         * @method onEvent
         * @param {Oskari.mapframework.event.Event} event a Oskari event object
         * Event is handled forwarded to correct #eventHandlers if found or discarded if not.
         */
        onEvent : function(event) {
            var me = this;
            var handler = me.eventHandlers[event.getName()];
            if(!handler) {
                return;
            }

            return handler.apply(this, [event]);
        },
        /**
         * @method stop
         * implements BundleInstance protocol stop method
         */
        stop : function() {
            var me = this;
            var sandbox = this.sandbox;
            for(var p in me.eventHandlers) {
                if(p) {
                    sandbox.unregisterFromEventByName(me, p);
                }
            }
            me.sandbox.unregister(me);
            me.started = false;
        }
    }, {
        /**
         * @property {String[]} protocol
         * @static
         */
        protocol : ['Oskari.bundle.BundleInstance', 'Oskari.mapframework.module.Module']
    });