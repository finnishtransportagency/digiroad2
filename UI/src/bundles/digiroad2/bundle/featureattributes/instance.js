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
    }, {
        /**
         * @static
         * @property __name
         */
        __name : 'Infobox',

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

            //me._featureDataTemplate = _.template('<li>{{name}}<input class="featureattribute" type="text" name="{{name}}" value="{{value}}"></li>');
            me._featureDataTemplate = _.template('<li>{{propertyName}}{{propertyValue}}</li>');
            me._featureDataTemplateText = _.template('<li>{{propertyName}}<input class="featureattribute" type="text" name="{{propertyName}}" value="{{propertyValue}}"></li>');
            me._featureDataTemplateChoice = _.template('<option value="{{propertyValue}}">{{propertyValue}}</option>');

            //me._featureDataTemplateHeader = _.template('<h3>{{propertyName}}</h3>');

            return null;
        },
        showAttributes : function(title, content, id) {
            var me = this;
            jQuery("#featureAttributes").html('<h2>' +title+ '</h2>'+ this._makeContent(content));
            jQuery("#featureAttributes li input").on("blur", function() {
                var data = jQuery(this);
                me._saveData(data.attr('name'), data.val());
            });

        },
        _saveData: function(name, value) {
            console.log(name + ':' +  value);
        },
        _makeContent: function(contents) {
            var me = this;

            /*
            var tmplItems = _.map(_.pairs(content), function(x) { return { name: x[0], value: x[1] };});
            var htmlContent = _.map(tmplItems, this._featureDataTemplate);
              */


            //jQuery('.featureattributes').on('click', updateValue(this));
            //jQuery(htmlContent).on('click', updateValue(this));

            var html = "";
            //TODO: change map to list

            /*var featureDataTemplate = _.template('<li>{{name}}<input class="featureattribute" type="text" name="{{name}}" value="{{value}}"></li>');
            var tmplItems = _.map(_.pairs(contents[content]), function(x) { return { id: x[0], name: x[1] , type: x[2], value: x[3]};});
            html = _.map(tmplItems, this._featureDataTemplate);
              */
            //for (var content in contents) {
                //html += '<li>'+ contents[content].propertyName + '<input class="featureattribute" type="text" name="'+ contents[content].propertyName+ '" value="' +contents[content].propertyValue+'"></li>';
            //}
            var options = "";
            _.forEach(contents,
                function (feature) {
                    if (feature.propertyType == "text") {
                        feature.propertyValue = feature.values[0].propertyValue;
                        html += me._featureDataTemplateText(feature);
                    } else if (feature.propertyType == "single_choice") {
                        options = "<select>";
                        _.forEach(feature.values,
                            function(optionValue) {
                                options +=  me._featureDataTemplateChoice(optionValue);
                            }
                        );
                        options +="</select>";
                        feature.propertyValue = options;
                        html += me._featureDataTemplate(feature);
                    } else if (feature.propertyType == "multiple_choice") {
                        options = "<select>";
                        _.forEach(feature.values,
                            function(optionValue) {
                                options +=  me._featureDataTemplateChoice(optionValue);
                            }
                        );
                        options +="</select>";
                        feature.propertyValue = options;
                        html += me._featureDataTemplate(feature);
                    }  else {
                        feature.propertyValue ="N/A";
                        html += me._featureDataTemplate(feature);
                    }
                    console.log(feature);
              }
            );
            console.log(html);



            return html;
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