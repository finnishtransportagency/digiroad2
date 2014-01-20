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
            this._showPanel();

        },
        /**
         * @method init
         * implements Module protocol init method - initializes request handlers
         */
        init : function() {
            var me = this;
            _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
            };
            me._panelControl = '<div class="panelControl">' +
                                   '<div class="panelControlLine"></div>' +
                                   '<div class="panelControlLine"></div>' +
                                   '<div class="panelControlLine"></div>' +
                               '</div>'+
                               '<div class="actionPanel">' +
                                    '<div class="layerGroup">' +
                                        '<div class="layerGroupImg">' +
                                            '<img src="/src/resources/digiroad2/bundle/actionpanel/images/bussi_valkoinen.png">' +
                                        '</div>' +
                                        '<div class="layerGroupLabel">Joukkoliikenteen pysäkit</div>' +
                                    '</div>' +
                                    '<div class="layerGroupLayers">' +
                                    '</div>' +
                               '</div>';

            me._mapBusStopLayer = _.template('<div class="busStopLayer">' +
                                                '<div class="busStopLayerCheckbox"><input class="layerSelector" type="checkbox" {{selected}} value="{{id}}"/></div>' +
                                                '<div class="busStopLayerName">{{name}}</div>' +
                                             '</div>');
            me._actionButtons =
                '<div class="actionButtons">' +
                    '<div data-action="Select" class="actionButton actionButtonActive actionPanelButtonSelect">' +
                        '<div class="actionPanelButtonSelectImage actionPanelButtonSelectActiveImage"></div>' +
                    '</div>' +
                    '<div data-action="Add" class="actionButton actionPanelButtonAdd">' +
                        '<div class="actionPanelButtonAddImage"></div>' +
                    '</div>' +
                    '<div data-action="Remove" class="actionButton actionPanelButtonRemove">' +
                        '<div class="actionPanelButtonRemoveImage"></div>' +
                    '</div>' +
                '</div>';

            return null;
        },
        _showPanel : function() {

            var me = this;
            var mapLayerService = me.getSandbox().getService('Oskari.mapframework.service.MapLayerService');
            var addLayers = function() {
                var layers = mapLayerService.getAllLayers();
                var selectedLayers = me.getSandbox().findAllSelectedMapLayers();
                jQuery("#maptools").html(me._panelControl);
                jQuery(".panelControl").on("click", function() {
                    jQuery(".actionPanel").toggleClass('actionPanelClosed');

                });
                _.forEach(layers, function (layer) {
                    if (layer.getLayerType() === 'busstoplayer') {
                        var selected = "";
                        if (_.contains(selectedLayers, layer)) {
                            selected = ' checked ';
                        }
                        jQuery(".layerGroupLayers").append(me._mapBusStopLayer({ selected: selected, id:layer.getId(), name: layer.getName()}));
                    }
                });
                jQuery(".layerSelector").on("change", function() {
                    var data = jQuery(this);
                    if (data.is(':checked')) {
                        me.getSandbox().postRequestByName('AddMapLayerRequest', [data.val(), false, false]);
                    } else {
                        me.getSandbox().postRequestByName('RemoveMapLayerRequest', [data.val()]);
                    }
                });
                jQuery(".actionPanel").append(me._actionButtons);

                jQuery(".actionButton").on("click", function() {
                    var data = jQuery(this);
                    var action = data.attr('data-action');
                    jQuery(".actionButtonActive").removeClass("actionButtonActive");
                    jQuery(".actionPanelButton"+action).addClass("actionButtonActive");
                    jQuery(".actionPanelButtonSelectActiveImage").removeClass("actionPanelButtonSelectActiveImage");
                    jQuery(".actionPanelButtonAddActiveImage").removeClass("actionPanelButtonAddActiveImage");
                    jQuery(".actionPanelButtonRemoveActiveImage").removeClass("actionPanelButtonRemoveActiveImage");
                    jQuery(".actionPanelButton"+action+"Image").addClass("actionPanelButton"+action+"ActiveImage");

                    var eventBuilder = me.getSandbox().getEventBuilder('actionpanel.ActionPanelToolSelectionChangedEvent');
                    var event = eventBuilder(action);
                    me.getSandbox().notifyAll(event);

                });

            };
            var error = function() {
                alert('Can\'t fetch map layers from server');
            };
            mapLayerService.loadAllLayersAjax(addLayers, error);
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