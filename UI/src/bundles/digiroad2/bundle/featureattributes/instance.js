/**
 * @class Oskari.digiroad2.bundle.featureattributes.FeatureAttributesBundleInstance
 *
 */
Oskari.clazz.define("Oskari.digiroad2.bundle.featureattributes.FeatureAttributesBundleInstance",

    /**
     * @method create called automatically on construction
     * @static
     */
        function() {
        this.sandbox = null;
        this.started = false;
        this.mediator = null;
        this._enumeratedPropertyValues = null;
        this._featureDataAssetId = null;
    }, {
        /**
         * @static
         * @property __name
         */
        __name : 'FeatureAttributes',

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

            sandbox.addRequestHandler('FeatureAttributes.ShowFeatureAttributesRequest', this.requestHandlers.showFeatureAttributesHandler);

        },
        /**
         * @method init
         * implements Module protocol init method - initializes request handlers
         */
        init : function() {
            var me = this;
            this.requestHandlers = {
                showFeatureAttributesHandler : Oskari.clazz.create('Oskari.digiroad2.bundle.featureattributes.request.ShowFeatureAttributesRequestHandler', this)
            };

            _.templateSettings = {
                interpolate: /\{\{(.+?)\}\}/g
            };

            me._featureDataTemplate = _.template('<li>{{propertyName}}{{propertyValue}}</li>');
            me._featureDataTemplateText = _.template('<li>{{propertyName}}<input class="featureattributeText" type="text"' +
                ' data-propertyId="{{propertyId}}" name="{{propertyName}}" value="{{values[0].propertyDisplayValue}}"></li>');
            me._featureDataTemplateChoice = _.template('<option {{selectedValue}} value="{{propertyValue}}">{{propertyDisplayValue}}</option>');
            me._getPropertyValues();

            return null;
        },
        showAttributes : function(id, content) {
            var me = this;
            me._featureDataAssetId = id;

            jQuery("#featureAttributes").html('<h2>' +id+ '</h2>'+ this._makeContent(content));

            jQuery(".featureattributeText").on("blur", function() {
                var data = jQuery(this);

                var propertyValue = [];
                propertyValue.push({
                    "propertyValue" : 0,
                    "propertyDisplayValue" : data.val()
                });

                me._saveTextData(propertyValue, data.attr('data-propertyId'));
            });

            jQuery(".featureattributeChoice").on("change", function() {
                var data = jQuery(this);
                var propertyValue = [];
                var values = data.val();

                _.forEach(values,
                    function(value) {
                        propertyValue.push({
                            "propertyValue" : Number(value),
                            "propertyDisplayValue" : ""
                        });
                    }
                );

                me._saveTextData(propertyValue, data.attr('data-propertyId'));
            });

        },
        _getPropertyValues: function() {
            var me = this;
            jQuery.getJSON("/api/enumeratedPropertyValues/10", function(data) {
                 me._enumeratedPropertyValues = data;
                 return data;
                })
                .fail(function() {
                    console.log( "error" );
                });
        },
        _saveTextData: function(propertyValue, propertyId) {
            jQuery.ajax({
                contentType: "application/json",
                type: "PUT",
                url: "/api/assets/"+this._featureDataAssetId+"/properties/"+propertyId+"/values",
                data: JSON.stringify(propertyValue),
                dataType:"json",
                success: function() {
                    console.log("done");
                },
                error: function() {
                    console.log("error");
                }
            });
        },
        _makeContent: function(contents) {
            var me = this;
            var html = "";
            _.forEach(contents,
                function (feature) {
                    if (feature.propertyType == "text") {
                        feature.propertyValue = feature.values[0].propertyValue;
                        html += me._featureDataTemplateText(feature);
                    } else if (feature.propertyType == "single_choice") {
                        feature.propertyValue = me._getSelect(feature.propertyName, feature.values, feature.propertyId, '');
                        html += me._featureDataTemplate(feature);
                    } else if (feature.propertyType == "multiple_choice") {
                        feature.propertyValue = me._getSelect(feature.propertyName, feature.values, feature.propertyId, 'multiple');
                        html += me._featureDataTemplate(feature);
                    }  else {
                        feature.propertyValue ="N/A";
                        html += me._featureDataTemplate(feature);
                    }
                }
            );

            return html;
        },
        _getSelect: function(name, values, propertyId, multiple) {
            var me = this;
            var options = '<select data-propertyId="'+propertyId+'" name="'+name+'" class="featureattributeChoice" ' + multiple +'>';
            var valuesNro = _.map(values, function(x) { return x.propertyValue;});

            var propertyValues = _.find(me._enumeratedPropertyValues,
                function(property) {
                    if (property.propertyId === propertyId) {
                        return property;
                    }
                }
            );

            _.forEach(propertyValues.values,
                function(optionValue) {
                    var selectedValue ='';

                    if (_.contains(valuesNro, optionValue.propertyValue)) {
                        selectedValue = 'selected="true"';
                    }

                    optionValue.selectedValue = selectedValue;
                    options +=  me._featureDataTemplateChoice(optionValue);
                }
            );

            options +="</select>";
            return options;
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
         * @property {Object} eventHandlers
         * @static
         */
        eventHandlers : {

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