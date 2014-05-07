(function() {
    var _enumeratedPropertyValues = null;
    var _readOnly = true;
    var _selectedAsset = {};
    _.templateSettings = {
        interpolate: /\{\{(.+?)\}\}/g
    };

    var _initializeEditExisting = function(asset) {
        // var featureAttributes = me._templates.featureDataWrapper({ header: busStopHeader(asset), streetView: streetView, attributes: featureData, controls: me._templates.featureDataEditControls({}) });
        var container = jQuery("#featureAttributes").empty();

        _selectedAsset = asset;

        // TODO: refactor this (duplication with _initializeCreateNew)
        var featureData = _makeContent(asset.propertyData);
        var streetView = $(_getStreetView(asset));

        var element = $('<div />').addClass('featureAttributesHeader').text(busStopHeader(asset));
        var wrapper = $('<div />').addClass('featureAttributesWrapper');
        wrapper.append(streetView.addClass('streetView')).append($('<div />').addClass('formContent').append(featureData));
        var featureAttributesElement = container.append(element).append(wrapper);
        _addDatePickers();

        var cancelBtn = $('<button />').addClass('cancel').text('Peruuta').click(function() {
            eventbus.trigger('asset:cancelled');
        });

        var saveBtn = $('<button />').addClass('save').text('Tallenna').click(function() {
            eventbus.trigger('asset:save');
        });

        // TODO: cleaner html
        featureAttributesElement.append($('<div />').addClass('formControls').append(cancelBtn).append(saveBtn));

        if (_readOnly) {
            $('#featureAttributes button').prop('disabled', true);
            $('#featureAttributes input').prop('disabled', true);
            $('#featureAttributes select').prop('disabled', true);
            $('#featureAttributes textarea').prop('disabled', true);
            $('#featureAttributes .formControls').hide();
        }

        function busStopHeader(asset) {
            if (_.isNumber(asset.externalId)) {
                return 'Valtakunnallinen ID: ' + asset.externalId;
            }
            else return 'Ei valtakunnallista ID:tä';
        }
    };

    var _changeAssetDirection = function(data) {
        var newValidityDirection = data.propertyData[0].values[0].propertyValue;
        var validityDirection = jQuery('.featureAttributeButton[data-publicId="vaikutussuunta"]');
        validityDirection.attr('value', newValidityDirection);
        _selectedAsset.validityDirection = newValidityDirection;
        jQuery('.streetView').html(_getStreetView(_selectedAsset));
    };

    var _getStreetView = function(asset) {
        var wgs84 = OpenLayers.Projection.transform(
            new OpenLayers.Geometry.Point(asset.lon, asset.lat),
            new OpenLayers.Projection('EPSG:3067'), new OpenLayers.Projection('EPSG:4326'));
        return streetViewTemplate({ wgs84X: wgs84.x, wgs84Y: wgs84.y, heading: (asset.validityDirection === 3 ? asset.bearing - 90 : asset.bearing + 90) });
    };

    var _addDatePickers = function () {
        var $validFrom = jQuery('#ensimmainen_voimassaolopaiva');
        var $validTo = jQuery('#viimeinen_voimassaolopaiva');
        if ($validFrom.length > 0 && $validTo.length > 0) {
            dateutil.addDependentDatePickers($validFrom, $validTo);
        }
    };

    window.Backend.getEnumeratedPropertyValues(10);

    var readOnlyHandler = function(property){
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
    };

    var textHandler = function(property){
        var input = $('<input type="text"/>').keyup(_.debounce(function(target){
            // tab press
            if(target.keyCode === 9){
                return;
            }

            // TODO: extract method
            eventbus.trigger('assetPropertyValue:changed',
                {
                    propertyData: [{
                        publicId: property.publicId,
                        values:  [{
                            propertyValue : target.currentTarget.value,
                            propertyDisplayValue : target.currentTarget.value
                        }]
                    }]
                });
        }, 500));

        var render = function(){
            // TODO: use cleaner html
            var outer = $('<div />').addClass('formAttributeContentRow');
            outer.append($('<div />').addClass('formLabels').text(property.localizedName));
            input.addClass('featureAttributeText');
            outer.append($('<div />').addClass('formAttributeContent').append(input));
            if(property.values[0]) {
                input.val(property.values[0].propertyDisplayValue);
            }
            // TODO: readonly handling
            return outer;
        };

        return {
            render: render
        };
    };

    var singleChoiceHandler = function(property, choices){
        function triggerEventBusChange(publicId, value) {
            eventbus.trigger('assetPropertyValue:changed', {
                propertyData: [
                    {
                        publicId: publicId,
                        values: [
                            {
                                propertyValue: value,
                                propertyDisplayValue: value
                            }
                        ]
                    }
                ]
            });
        }

        var enumValues = _.find(choices, function(choice){
            return choice.publicId === property.publicId;
        }).values;

        var input = $('<select />').addClass('featureattributeChoice').change(function(x){
            triggerEventBusChange(property.publicId, x.currentTarget.value);
        });

        var readOnlyText = $('<span />');
        var render = function(){
            //TODO: cleaner html
            var label = $('<div />').addClass('formLabels');
            label.text(property.localizedName);
            _.forEach(enumValues, function(x) {
                var attr = $('<option>').text(x.propertyDisplayValue).attr('value', x.propertyValue);
                input.append(attr);
            });
            if(property.values && property.values[0]) {
                input.val(property.values[0].propertyValue);
                readOnlyText.text(property.values[0].propertyDisplayValue);
            }
            var wrapper = $('<div />').addClass('formAttributeContent');
            // TODO: readonly
            return $('<div />').addClass('formAttributeContentRow').append(label).append(wrapper.append(input));
        };

        return {
            render: render
        };
    };

    var directionChoiceHandler = function(property){
        // TODO: ugliness, remove
        var validityDirection = 2;
        var input = $('<button />').addClass('featureAttributeButton').text('Vaihda suuntaa').click(function(){
            validityDirection = validityDirection === 2 ? 3 : 2;
            //TODO: update streetview without using globals
            _selectedAsset.validityDirection = validityDirection;
            eventbus.trigger('assetPropertyValue:changed', {
                propertyData: [{
                    publicId: property.publicId,
                    values:  [{
                        propertyValue : validityDirection,
                        propertyDisplayValue : validityDirection
                    }]
                }]
            });
            jQuery('.streetView').empty().append($(_getStreetView(_selectedAsset)));
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
    };

    var dateHandler = function(property){
        var input = $('<input />').attr('id', property.publicId).on('keyup datechange', _.debounce(function(target){
            // tab press
            if(target.keyCode === 9){
                return;
            }

            // TODO: extract method
            eventbus.trigger('assetPropertyValue:changed',
                {
                    propertyData: [{
                        publicId: property.publicId,
                        values:  [{
                            propertyValue : dateutil.finnishToIso8601(target.currentTarget.value),
                            propertyDisplayValue : dateutil.finnishToIso8601(target.currentTarget.value)
                        }]
                    }]
                });
        }, 500));

        var render = function(){
            //TODO: cleaner html
            var outer = $('<div />').addClass('formAttributeContentRow');

            var label = $('<div />').addClass('formLabels').text(property.localizedName);
            if(property.values[0]) {
                input.val(dateutil.iso8601toFinnish(property.values[0].propertyDisplayValue));
            }
            input.addClass('featureAttributeDate');
            // TODO: readonly
            return outer.append(label).append(outer.append($('<div />').addClass('formAttributeContent').append(input)));
        };

        return {
            render: render
        };
    };

    var multiChoiceHandler = function(property, choices){
        var currentValue = _.cloneDeep(property);
        var enumValues = _.chain(choices)
            .filter(function(choice){
                return choice.publicId === property.publicId;
            })
            .flatten('values')
            .filter(function(x){
                return x.propertyValue !== '99';
            }).value();
        var render = function(){
            var container = $('<div />').addClass('formAttributeContentRow');
            container.append($('<div />').addClass('formLabels').text(property.localizedName));
            var inputContainer = $('<div />').addClass('featureattributeChoice');
            _.forEach(enumValues, function(x) {
                var input = $('<input type="checkbox" />').change(function(evt){
                    x.checked = evt.currentTarget.checked;
                    eventbus.trigger('assetPropertyValue:changed',
                        {
                            propertyData: [{
                                publicId: property.publicId,
                                values: _.chain(enumValues)
                                    .filter(function(value) { return value.checked; })
                                    .map(function(value) {
                                        return {
                                            propertyValue: parseInt(value.propertyValue, 10),
                                            propertyDisplayValue: value.propertyDisplayValue };
                                    })
                                    .value()
                            }]
                        });
                });
                x.checked = _.any(currentValue.values, function(prop){
                    return prop.propertyValue === x.propertyValue;
                });

                input.prop('checked', x.checked);
                var label = $('<label />').text(x.propertyDisplayValue);
                inputContainer.append(input).append(label).append($('<br>'));
            });

            return container.append($('<div />').addClass('formAttributeContent').append(inputContainer));
        };

        return {
            render: render
        };
    };

    var _makeContent = function(contents) {
        var html = $('<div />');
        _.forEach(contents,
            function (feature) {
                feature.localizedName = window.localizedStrings[feature.publicId];
                var propertyType = feature.propertyType;
                if (propertyType === "text" || propertyType === "long_text") {
                    // TODO: check long text
                    html.append(textHandler(feature).render());
                } else if (propertyType === "read_only_text") {
                    html.append(readOnlyHandler(feature).render());
                } else if (propertyType === "single_choice" && feature.publicId !== 'vaikutussuunta') {
                    html.append(singleChoiceHandler(feature, _enumeratedPropertyValues).render());
                } else if (feature.publicId === 'vaikutussuunta') {
                    html.append(directionChoiceHandler(feature).render());
                } else if (feature.propertyType === "multiple_choice") {
                    html.append(multiChoiceHandler(feature, _enumeratedPropertyValues).render());
                } else if (propertyType === "date") {
                    html.append(dateHandler(feature).render());
                }  else {
                    feature.propertyValue ='Ei toteutettu';
                    html.append($(featureDataTemplateNA(feature)));
                }
            }
        );
        return html;
    };

    var streetViewTemplate  = _.template(
            '<a target="_blank" href="http://maps.google.com/?ll={{wgs84Y}},{{wgs84X}}&cbll={{wgs84Y}},{{wgs84X}}&cbp=12,{{heading}}.09,,0,5&layer=c&t=m">' +
            '<img alt="Google StreetView-näkymä" src="http://maps.googleapis.com/maps/api/streetview?key=AIzaSyBh5EvtzXZ1vVLLyJ4kxKhVRhNAq-_eobY&size=360x180&location={{wgs84Y}}' +
            ', {{wgs84X}}&fov=110&heading={{heading}}&pitch=-10&sensor=false">' +
            '</a>');

    var featureDataTemplateNA = _.template('<div class="formAttributeContentRow">' +
        '<div class="formLabels">{{localizedName}}</div>' +
        '<div class="featureAttributeNA">{{propertyValue}}</div>' +
        '</div>');

    var _closeAsset = function() {
        jQuery("#featureAttributes").html('');
        dateutil.removeDatePickersFromDom();
        _selectedAsset = null;
    };

    eventbus.on('asset:fetched assetPropertyValue:fetched asset:created asset:initialized', _initializeEditExisting, this);
    eventbus.on('asset:unselected', _closeAsset, this);
    eventbus.on('layer:selected', _closeAsset, this);

    eventbus.on('assetPropertyValue:changed', function(data) {
        if (data.propertyData[0].publicId == 'vaikutussuunta') {
            _changeAssetDirection(data);
        }
    }, this);

    eventbus.on('application:readOnly', function(readOnly) {
        _readOnly = readOnly;
    }, this);

    eventbus.on('validityPeriod:changed', function(validityPeriods) {
        if (_selectedAsset && !_.contains(validityPeriods, _selectedAsset.validityPeriod)) {
            _closeAsset();
        }
    }, this);

    eventbus.on('enumeratedPropertyValues:fetched', function(values) {
        _enumeratedPropertyValues = values;
    }, this);

    eventbus.on('asset:moved', function(position) {
        _selectedAsset.lon = position.lon;
        _selectedAsset.lat = position.lat;
        _selectedAsset.bearing = position.bearing;
        _selectedAsset.roadLinkId = position.roadLinkId;
        jQuery('.streetView').html(_getStreetView(_selectedAsset));
    }, this);
})();

