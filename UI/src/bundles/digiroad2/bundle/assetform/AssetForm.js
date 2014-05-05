Oskari.clazz.define("Oskari.digiroad2.bundle.assetform.AssetForm",

    function(config) {
        this.sandbox = null;
        this.started = false;
        this.mediator = null;
        this._enumeratedPropertyValues = null;
        this._featureDataAssetId = null;
        this._backend = defineDependency('backend', window.Backend);
        this._readOnly = true;

        function defineDependency(dependencyName, defaultImplementation) {
            var dependency = _.isObject(config) ? config[dependencyName] : null;
            return dependency || defaultImplementation;
        }
    }, {
        __name : 'AssetForm',

        getName : function() {
            return this.__name;
        },
        setSandbox : function(sandbox) {
            this.sandbox = sandbox;
        },
        getSandbox : function() {
            return this.sandbox;
        },
        update : function() {
        },
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
                if(me.eventHandlers.hasOwnProperty(p)) {
                    sandbox.registerForEventByName(me, p);
                }
            }
        },
        init : function() {
            eventbus.on('asset:fetched assetPropertyValue:fetched asset:created', this._initializeEditExisting, this);
            eventbus.on('asset:unselected', this._closeAsset, this);
            eventbus.on('layer:selected', this._closeAsset, this);
            eventbus.on('asset:placed', function(asset) {
                this._selectedAsset = asset;
                this._backend.getAssetTypeProperties(10);
            }, this);
            eventbus.on('assetTypeProperties:fetched', function(properties) {
                this._initializeCreateNew(properties);
            }, this);
            eventbus.on('assetPropertyValue:changed', function(data) {
                if (data.propertyData[0].publicId == 'vaikutussuunta') {
                    this._changeAssetDirection(data);
                }
            }, this);
            eventbus.on('application:readOnly', function(readOnly) {
                this._readOnly = readOnly;
            }, this);
            eventbus.on('validityPeriod:changed', function(validityPeriods) {
                if (this._selectedAsset && !_.contains(validityPeriods, this._selectedAsset.validityPeriod)) {
                  this._closeAsset();
                }
            }, this);
            eventbus.on('enumeratedPropertyValues:fetched', function(values) {
                this._enumeratedPropertyValues = values;
            }, this);
            eventbus.on('asset:moved', function(position) {
                this._selectedAsset.lon = position.lon;
                this._selectedAsset.lat = position.lat;
                this._selectedAsset.bearing = position.bearing;
                this._selectedAsset.roadLinkId = position.roadLinkId;
                jQuery('.streetView').html(this._getStreetView(this._selectedAsset));
            }, this);
            
            this._templates = Oskari.clazz.create('Oskari.digiroad2.bundle.assetform.template.Templates');
            this._getPropertyValues();

            return null;
        },
        _initializeEditExisting : function(asset) {
            var me = this;
            this._selectedAsset = asset;
            me._featureDataAssetId = asset.id;

            // TODO: refactor this (duplication with _initializeCreateNew)
            var featureData = me._makeContent(asset.propertyData);
            var streetView = $(me._getStreetView(asset));

            var element = $('<div />').addClass('featureAttributesHeader').text(busStopHeader(asset));
            var wrapper = $('<div />').addClass('featureAttributesWrapper');
            wrapper.append(streetView.addClass('streetView')).append($('<div />').addClass('formContent').append(featureData));
            // var featureAttributes = me._templates.featureDataWrapper({ header: busStopHeader(asset), streetView: streetView, attributes: featureData, controls: me._templates.featureDataEditControls({}) });
            var featureAttributesElement = jQuery("#featureAttributes").append(element).append(wrapper);
            me._addDatePickers();
            if (this._readOnly) {
              $('#featureAttributes button').prop('disabled', true);
              $('#featureAttributes input').prop('disabled', true);
              $('#featureAttributes select').prop('disabled', true);
              $('#featureAttributes textarea').prop('disabled', true);
              $('#featureAttributes .formControls').hide();
            }
            me.bindEventHandlersForChanges(featureAttributesElement);

            featureAttributesElement.find('button.cancel').on('click', function() {
                eventbus.trigger('asset:cancelled');
                eventbus.trigger('asset:unselected');
                me._closeAsset();
                me._backend.getAsset(asset.id);
            });

            featureAttributesElement.find('button.save').on('click', function() {
                me._activateSaveModal(featureAttributesElement);
                me._updateAsset(asset.id, featureAttributesElement);
            });

            function busStopHeader(asset) {
                if (_.isNumber(asset.externalId)) {
                    return 'Valtakunnallinen ID: ' + asset.externalId;
                }
                else return 'Ei valtakunnallista ID:tä';
            }
        },
        
        _changeAssetDirection: function(data) {
            var newValidityDirection = data.propertyData[0].values[0].propertyValue;
            var validityDirection = jQuery('.featureAttributeButton[data-publicId="vaikutussuunta"]');
            validityDirection.attr('value', newValidityDirection);
            this._selectedAsset.validityDirection = newValidityDirection;
            jQuery('.streetView').html(this._getStreetView(this._selectedAsset));
        },
        
        _initializeCreateNew: function(properties) {
            var me = this;
            // var featureAttributesElement = jQuery('#featureAttributes');

            // TODO: refactor this (duplication with _initializeEditExisting)
            var featureData = me._makeContent(properties);
            var streetView = $(me._getStreetView(this._selectedAsset));

            var element = $('<div />').addClass('featureAttributesHeader').text('Uusi pysäkki');
            var wrapper = $('<div />').addClass('featureAttributesWrapper');
            wrapper.append(streetView.addClass('streetView')).append($('<div />').addClass('formContent').append(featureData));
            // var featureAttributes = me._templates.featureDataWrapper({ header: busStopHeader(asset), streetView: streetView, attributes: featureData, controls: me._templates.featureDataEditControls({}) });
            var featureAttributesElement = jQuery("#featureAttributes").append(element).append(wrapper);

            me._addDatePickers();

            me.bindEventHandlersForChanges(featureAttributesElement);

            featureAttributesElement.find('button.cancel').on('click', function() {
                eventbus.trigger('asset:cancelled');
                eventbus.trigger('asset:unselected');
                me._closeAsset();
            });

            featureAttributesElement.find('button.save').on('click', function() {
                me._activateSaveModal(featureAttributesElement);
                me._saveNewAsset(featureAttributesElement);
            });
        },
        bindEventHandlersForChanges: function(featureAttributesElement) {

            var gatherPropertyChangedPayload = function(publicId, values) {
                var payload = {
                    propertyData: [{
                        publicId: publicId,
                        values: values
                    }]
                };
                return payload;
            };

            var triggerChanges = function(publicId, values) {
                eventbus.trigger('assetPropertyValue:changed', gatherPropertyChangedPayload(publicId, values));
            };

            var handleChange = function(jqElement, valueDomExtractor) {
                var values = valueDomExtractor(jqElement);
                triggerChanges(jqElement.attr('data-publicId'), values);
            };

            var me = this;
            var validityDirection = featureAttributesElement.find('.featureAttributeButton[data-publicId="vaikutussuunta"]');
            var assetDirectionChangedHandler = function() {
                var value = validityDirection.attr('value');
                var newValidityDirection = validityDirection.attr('value') == 2 ? 3 : 2;
                triggerChanges(validityDirection.attr('data-publicId'),[{propertyValue: newValidityDirection}]);
            };
            validityDirection.click(assetDirectionChangedHandler);

            featureAttributesElement.find('input.featureAttributeText').on('keyup', function() {
                var jqElement = jQuery(this);
                handleChange(jqElement, me._propertyValuesOfTextElement);
            });

            featureAttributesElement.find('textarea.featureAttributeLongText').on('keyup', function() {
                var jqElement = jQuery(this);
                handleChange(jqElement, me._propertyValuesOfTextElement);
            });

            featureAttributesElement.find('input.featureAttributeDate').on('change' ,function() {
                var jqElement = jQuery(this);
                handleChange(jqElement, me._propertyValuesOfTextElement);
            });

            featureAttributesElement.find('div.featureattributeChoice').on('change', function() {
                var jqElement = jQuery(this);
                handleChange(jqElement, me._propertyValuesOfMultiCheckboxElement);
            });

            featureAttributesElement.find('div.formAttributeContent').find('select').on('change', function() {
                var jqElement = jQuery(this);
                handleChange(jqElement, me._propertyValuesOfSelectionElement);
            });
        },
        _saveNewAsset: function(featureAttributesElement) {
            var me = this;
            var properties = me._collectAssetProperties(featureAttributesElement);
            me._backend.createAsset(
                {assetTypeId: 10,
                    lon: me._selectedAsset.lon,
                    lat: me._selectedAsset.lat,
                    roadLinkId:  me._selectedAsset.roadLinkId,
                    bearing:  me._selectedAsset.bearing,
                    properties: properties});
        },
        _updateAsset: function(assetId, featureAttributesElement) {
            var me = this;
            // TODO: only save changed properties and position
            var properties = this._collectAssetProperties(featureAttributesElement);
            me._backend.updateAsset(assetId, {
                assetTypeId: me._selectedAsset.assetTypeId,
                lon: me._selectedAsset.lon,
                lat: me._selectedAsset.lat,
                roadLinkId: me._selectedAsset.roadLinkId,
                bearing: me._selectedAsset.bearing,
                properties: properties});
        },
        _collectAssetProperties: function(featureAttributesElement) {
            var me = this;
            var textElements = featureAttributesElement.find('.featureAttributeText , .featureAttributeLongText');
            var textElementAttributes = _.map(textElements, function(textElement) {
                var jqElement = jQuery(textElement);
                return {
                    publicId: jqElement.attr('data-publicId'),
                    propertyValues: me._propertyValuesOfTextElement(jqElement)
                };
            });

            var buttonElements = featureAttributesElement.find('.featureAttributeButton');
            var buttonElementAttributes = _.map(buttonElements, function(buttonElement) {
                var jqElement = jQuery(buttonElement);
                return {
                    publicId: jqElement.attr('data-publicId'),
                    propertyValues: me._propertyValuesOfButtonElement(jqElement)
                };
            });

            var selectionElements = featureAttributesElement.find('select.featureattributeChoice');
            var selectionElementAttributes = _.map(selectionElements, function(selectionElement) {
                var jqElement = jQuery(selectionElement);
                return {
                    publicId: jqElement.attr('data-publicId'),
                    propertyValues: me._propertyValuesOfSelectionElement(jqElement)
                };
            });

            var multiCheckboxElements = featureAttributesElement.find('div.featureattributeChoice');
            var multiCheckboxElementAttributes = _.map(multiCheckboxElements, function(multiCheckboxElement) {
                var jqElement = jQuery(multiCheckboxElement);
                return {
                    publicId: jqElement.attr('data-publicId'),
                    propertyValues: me._propertyValuesOfMultiCheckboxElement(jqElement)
                };
            });

            var dateElements = featureAttributesElement.find('.featureAttributeDate');
            var dateElementAttributes = _.map(dateElements, function(dateElement) {
                var jqElement = jQuery(dateElement);
                return {
                    publicId: jqElement.attr('data-publicId'),
                    propertyValues: me._propertyValuesOfDateElement(jqElement)
                };
            });
            var all = textElementAttributes
                .concat(selectionElementAttributes)
                .concat(buttonElementAttributes)
                .concat(multiCheckboxElementAttributes)
                .concat(dateElementAttributes);
            var properties = _.chain(all)
                .map(function(attr) {
                    return {publicId: attr.publicId,
                        values: attr.propertyValues};
                })
                .map(function(p) {
                    if (p.publicId == 'pysakin_tyyppi' && _.isEmpty(p.values)) {
                        p.values = [{propertyDisplayValue: "Pysäkin tyyppi",
                            propertyValue: 99}];
                    }
                    return p;
                }).value();
            return properties;
        },
        _getStreetView: function(asset) {
            var wgs84 = OpenLayers.Projection.transform(
                new OpenLayers.Geometry.Point(asset.lon, asset.lat),
                new OpenLayers.Projection('EPSG:3067'), new OpenLayers.Projection('EPSG:4326'));
            return this._templates.streetViewTemplate({ wgs84X: wgs84.x, wgs84Y: wgs84.y, heading: (asset.validityDirection === 3 ? asset.bearing - 90 : asset.bearing + 90) });
        },
        _addDatePickers: function () {
            var $validFrom = jQuery('.featureAttributeDate[data-publicId=ensimmainen_voimassaolopaiva]');
            var $validTo = jQuery('.featureAttributeDate[data-publicId=viimeinen_voimassaolopaiva]');
            if ($validFrom.length > 0 && $validTo.length > 0) {
                dateutil.addDependentDatePickers($validFrom, $validTo);
            }
        },
        _propertyValuesOfTextElement: function(element) {
            return [{
                propertyValue : element.val(),
                propertyDisplayValue : element.val()
            }];
        },
        _propertyValuesOfButtonElement: function(element) {
            return [{
                propertyValue: element.attr('value') == 2 ? 2 : 3,
                propertyDisplayValue: element.attr('name')
            }];
        },
        _propertyValuesOfSelectionElement: function(element) {
            return [{
                propertyValue : Number(element.val()),
                propertyDisplayValue : element.attr('name')
            }];
        },
        _propertyValuesOfMultiCheckboxElement: function(element) {
            return _.chain(element.find('input'))
                    .filter(function(childElement) {
                        return $(childElement).is(':checked');
                    })
                    .map(function(childElement) {
                      return {
                        propertyValue: Number(childElement.value),
                        propertyDisplayValue: element.attr('name')
                      };
                    })
                    .value();
        },

        _propertyValuesOfDateElement: function(element) {
            return _.isEmpty(element.val()) ? [] : [{
                propertyValue : dateutil.finnishToIso8601(element.val()),
                propertyDisplayValue : dateutil.finnishToIso8601(element.val())
            }];
        },
        _getPropertyValues: function() {
            var me = this;
            me._backend.getEnumeratedPropertyValues(10);
        },
        _savePropertyData: function(propertyValues, publicId) {
            var propertyValue;
            if (publicId == 'pysakin_tyyppi' && _.isEmpty(propertyValues)) {
                propertyValue = [{ propertyValue: 99 }];
            } else {
                propertyValue = propertyValues;
            }
            var me = this;
            me._backend.putAssetPropertyValue(this._featureDataAssetId, publicId, propertyValue);
        },

        readOnlyHandler : function(property){
            var render = function(){
                var propertyVal = _.isEmpty(property.values) === false ? property.values[0].propertyValue : '';
                // TODO: hack, because form is rendered using html as string
                // TODO: use cleaner html
                return jQuery('<div />').addClass('formAttributeContentRow')
                                     .addClass('readOnlyRow').text(property.localizedName + ': ' + propertyVal);
            };

            return {
                render: render
            };
        },

        textHandler : function(property){
            var html = $('<div />');
            var label = $('<label />');
            var input = $('<input type="text"/>').keyup(_.debounce(function(target){
                // tab press
                if(target.keyCode === 9){
                    return;
                }
                //TODO: trigger eventbus change
                console.log('Send change to eventbus');
                console.log({ publicId: property.publicId, val: target.currentTarget.value });
            }, 500));
            var readOnlyText = $('<span />');

            var render = function(){
                // TODO: use cleaner html
                var outer = $('<div />').addClass('formAttributeContentRow');
                outer.append($('<div />').addClass('formLabels').text(property.localizedName));
                input.addClass('featureAttributeText');
                outer.append($('<div />').addClass('formAttributeContent').append(input));
                // label.text(getLabel(property.publicId));
                if(property.values[0]) {
                    input.val(property.values[0].propertyDisplayValue);
                }
                // TODO: readonly handling
                return outer;
            };

            return {
                render: render
            };
        },


        singleChoiceHandler : function(property, choices){
            var enumValues = _.find(choices, function(choice){
                return choice.publicId === property.publicId;
            }).values;

            var input = $('<select />').addClass('featureattributeChoice').change(function(x){
                var prop = _.find(enumValues, function(enumVal){
                    return enumVal.propertyValue === x.currentTarget.value;
                });
                //TODO: trigger eventbus change
                console.log({ publicId: property.publicId, val: x.currentTarget.value, prop: prop });
            });

            var readOnlyText = $('<span />');
            var render = function(){
                //TODO: cleaner html
                var label = $('<div />').addClass('formLabels');
                label.text(property.localizedName);
                _.forEach(enumValues, function(x) {
                    var attr = $('<option>').text(x.propertyDisplayValue).attr('value', x.propertyValue);
                    attr.propertyVal = x;
                    input.append(attr);
                });
                if(property.values && property.values[0]) {
                    input.val(property.values[0].propertyValue);
                    readOnlyText.text(property.values[0].propertyDisplayValue);
                    // currentValue = property.values[0].propertyValue;
                }
                var wrapper = $('<div />').addClass('formAttributeContent');
                // TODO: readonly
                return $('<div />').addClass('formAttributeContentRow').append(label).append(wrapper.append(input));
            };

            return {
                render: render
            };
        },

        directionChoiceHandler : function(property){
            // TODO: ugliness, remove
            var me = this;
            var validityDirection = 2;
            var input = $('<button />').addClass('featureAttributeButton').text('Vaihda suuntaa').click(function(){
                validityDirection = validityDirection === 2 ? 3 : 2;
                //TODO: update streetview without using globals
                me._selectedAsset.validityDirection = validityDirection;
                jQuery('.streetView').empty().append($(me._getStreetView(me._selectedAsset)));
                //TODO: trigger eventbus change
                console.log({ publicId: property.publicId, val: validityDirection });
            });

            var render = function(){
                //TODO: cleaner html
                var label = $('<div />').addClass('formLabels');
                label.text(property.localizedName);
                if(property.values && property.values[0]) {
                    validityDirection = property.values[0].propertyValue;
                }
                var wrapper = $('<div />').addClass('formAttributeContent');
                // TODO: readonly
                return $('<div />').addClass('formAttributeContentRow').append(label).append(wrapper.append(input));
            };

            return {
                render: render
            };
        },

        _makeContent: function(contents) {
            var me = this;
            var html = $('<div />');
            _.forEach(contents,
                function (feature) {
                    feature.localizedName = window.localizedStrings[feature.publicId];
                    var propertyType = feature.propertyType;
                    if (propertyType === "text" || propertyType === "long_text") {
                        // TODO: check long text
                        html.append(me.textHandler(feature).render());
                    } else if (propertyType === "read_only_text") {
                        html.append(me.readOnlyHandler(feature).render());
                    } else if (propertyType === "single_choice" && feature.publicId !== 'vaikutussuunta') {
                        html.append(me.singleChoiceHandler(feature, me._enumeratedPropertyValues).render());
                    } else if (feature.publicId === 'vaikutussuunta') {
                        html.append(me.directionChoiceHandler(feature).render());
                    } else if (feature.propertyType === "multiple_choice") {
                        feature.propertyValue = me._getMultiCheckbox(feature.publicId, feature.values, feature.publicId);
                        html.append($(me._templates.featureDataTemplate(feature)));
                    } else if (propertyType === "date") {
                        feature.propertyValue = "";
                        feature.propertyDisplayValue = "";
                        if (feature.values[0]) {
                            feature.propertyValue = dateutil.iso8601toFinnish(feature.values[0].propertyDisplayValue);
                            feature.propertyDisplayValue = dateutil.iso8601toFinnish(feature.values[0].propertyDisplayValue);
                        }
                        html.append($(me._templates.featureDataTemplateDate(feature)));
                    }  else {
                        feature.propertyValue ='Ei toteutettu';
                        html.append($(me._templates.featureDataTemplateNA(feature)));
                    }
                }
            );
            return html;
        },
        _getTextTemplate: function(propertyType, feature) {
            return propertyType === "long_text" ? this._templates.featureDataTemplateLongText(feature) : this._templates.featureDataTemplateText(feature);
        },
        _getSelect: function(name, values, publicId, multiple) {
            var me = this;
            var options = '<select data-publicId="'+publicId+'" name="'+name+'" class="featureattributeChoice" ' + multiple +'>';
            var valuesNro = _.map(values, function(x) { return x.propertyValue;});
            var selected = _.size(valuesNro) > 0 ? valuesNro : ['99']; // default value 99 if none is selected
            var propertyValues = _.find(me._enumeratedPropertyValues, function(property) { return property.publicId === publicId; });
            _.forEach(propertyValues.values,
                function(optionValue) {
                    var selectedValue ='';
                    if (_.contains(selected, optionValue.propertyValue)) {
                        selectedValue = 'selected="true" ';
                    }
                    optionValue.selectedValue = selectedValue;
                    options +=  me._templates.featureDataTemplateChoice(optionValue);
                }
            );

            options +="</select>";
            return options;
        },
        _activateSaveModal: function(featureAttributesElement) {
            featureAttributesElement.append('<div class="featureAttributesDisabled">&nbsp;</div>');
        },
        _getMultiCheckbox: function(name, values, publicId) {
            var invalidValue = '99';
            var me = this;
            var checkboxes = '<div data-publicId="' + publicId +
                '" name="' + name +
                '" class="featureattributeChoice">';
            var valuesNro = _.pluck(values, 'propertyValue');
            var propertyValues = _.find(me._enumeratedPropertyValues, function(property) { return property.publicId === publicId; });
            _.forEach(propertyValues.values,
                function(inputValue) {
                    if (invalidValue !== inputValue.propertyValue) {
                        var checkedValue = '';
                        if (_.contains(valuesNro, inputValue.propertyValue)) {
                            checkedValue = 'checked ';
                        }
                        inputValue.checkedValue = checkedValue;
                        inputValue.publicId = publicId;
                        inputValue.name = publicId + '_' + inputValue.propertyValue;
                        checkboxes +=  me._templates.featureDataTemplateCheckbox(inputValue);
                    }
                }
            );

            checkboxes +="</div>";
            return checkboxes;
        },
        onEvent : function(event) {
            var me = this;
            var handler = me.eventHandlers[event.getName()];
            if(handler) {
                return handler.apply(this, [event]);
            }
            return undefined;
        },
        _closeAsset: function() {
            jQuery("#featureAttributes").html('');
            dateutil.removeDatePickersFromDom();
            this._selectedAsset = null;
        },
        stop : function() {
            var me = this;
            var sandbox = this.sandbox;
            for(var p in me.eventHandlers) {
                if(me.eventHandlers.hasOwnProperty(p)) {
                    sandbox.unregisterFromEventByName(me, p);
                }
            }
            me.sandbox.unregister(me);
            me.started = false;
        }
    }, {
        protocol : ['Oskari.bundle.BundleInstance', 'Oskari.mapframework.module.Module']
    });

