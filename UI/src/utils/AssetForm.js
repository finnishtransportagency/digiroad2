(function() {
    var enumeratedPropertyValues = null;
    var readonly = true;
    var selectedAsset = {};
    var streetViewHandler;
    var activeLayer = 'asset';

    _.templateSettings = {
        interpolate: /\{\{(.+?)\}\}/g
    };

    var renderAssetForm = function(asset) {
        if (activeLayer !== 'asset') {
            return;
        }
        var container = $("#featureAttributes").empty();

        var element = $('<div />').addClass('featureAttributesHeader').text(busStopHeader(asset));
        var wrapper = $('<div />').addClass('featureAttributesWrapper');
        streetViewHandler = getStreetView(asset);
        wrapper.append(streetViewHandler.render()).append($('<div />').addClass('formContent').append(getAssetForm(asset.propertyData)));
        var featureAttributesElement = container.append(element).append(wrapper);
        addDatePickers();

        var cancelBtn = $('<button />').addClass('cancel').text('Peruuta').click(function() {
            $("#featureAttributes").empty();
            eventbus.trigger('asset:cancelled');
        });

        var saveBtn = $('<button />').addClass('save').text('Tallenna').click(function() {
            selectedAssetController.save();
        });

        // TODO: cleaner html
        featureAttributesElement.append($('<div />').addClass('formControls').append(cancelBtn).append(saveBtn));

        if (readonly) {
            $('#featureAttributes .formControls').hide();
        }

        function busStopHeader(asset) {
            if (_.isNumber(asset.externalId)) {
                return 'Valtakunnallinen ID: ' + asset.externalId;
            }
            else return 'Ei valtakunnallista ID:tä';
        }
    };

    var getStreetView = function(assetForInit) {
        var asset = _.cloneDeep(assetForInit);
        var render = function() {
            var wgs84 = OpenLayers.Projection.transform(
                new OpenLayers.Geometry.Point(asset.lon, asset.lat),
                new OpenLayers.Projection('EPSG:3067'), new OpenLayers.Projection('EPSG:4326'));
            return $(streetViewTemplate({ wgs84X: wgs84.x, wgs84Y: wgs84.y, heading: (asset.validityDirection === 3 ? asset.bearing - 90 : asset.bearing + 90) }))
                    .addClass('streetView');
        };

        var changeDirection = function(newDirection){
            asset.validityDirection = newDirection;
            $('.streetView').empty().append(render());
        };

        var changePosition = function(position){
            asset.lon = position.lon;
            asset.lat = position.lat;
            asset.bearing = position.bearing;
            $('.streetView').empty().append(render());
        };

        return {
            render: render,
            changeDirection: changeDirection,
            changePosition: changePosition
        };
    };

    var addDatePickers = function () {
        var $validFrom = $('#ensimmainen_voimassaolopaiva');
        var $validTo = $('#viimeinen_voimassaolopaiva');
        if ($validFrom.length > 0 && $validTo.length > 0) {
            dateutil.addDependentDatePickers($validFrom, $validTo);
        }
    };

    var readOnlyHandler = function(property){
        var outer = $('<div />').addClass('formAttributeContentRow');
        var propertyVal = _.isEmpty(property.values) === false ? property.values[0].propertyDisplayValue : '';
        // TODO: use cleaner html
        if (property.propertyType === 'read_only_text') {
            outer.addClass('readOnlyRow').text(property.localizedName + ': ' + propertyVal);
        } else {
            outer.append($('<div />').addClass('formLabels').text(property.localizedName));
            outer.append($('<div />').addClass('formAttributeContent readOnlyText').attr('disabled', readonly).text(propertyVal));
        }
        return outer;
    };

    var triggerEventBusChange = function(publicId, values) {
        eventbus.trigger('assetPropertyValue:changed', {
            propertyData: [
                {
                    publicId: publicId,
                    values: values
                }
            ]
        });
    };

    var textHandler = function(property){
        var inputElement = property.propertyType === 'long_text' ?
                $('<textarea />').addClass('featureAttributeLongText') : $('<input type="text"/>').addClass('featureAttributeText');
        var input = inputElement.keyup(_.debounce(function(target){
            // tab press
            if(target.keyCode === 9){
                return;
            }
            triggerEventBusChange(property.publicId, [{ propertyValue: target.currentTarget.value }]);
        }, 500));

        // TODO: use cleaner html
        var outer = $('<div />').addClass('formAttributeContentRow').attr('data-required', property.required);
        outer.append($('<div />').addClass('formLabels').text(property.localizedName));

        outer.append($('<div />').addClass('formAttributeContent').append(input));
        if(property.values[0]) {
            input.val(property.values[0].propertyDisplayValue);
        }
        input.attr('disabled', readonly);
        return outer;
    };

    var singleChoiceHandler = function(property, choices){
        var enumValues = _.find(choices, function(choice){
            return choice.publicId === property.publicId;
        }).values;

        var input = $('<select />').addClass('featureattributeChoice').change(function(x){
            triggerEventBusChange(property.publicId, [{ propertyValue: x.currentTarget.value }]);
        });

        //TODO: cleaner html
        var label = $('<div />').addClass('formLabels');
        label.text(property.localizedName);
        _.forEach(enumValues, function(x) {
            var attr = $('<option>').text(x.propertyDisplayValue).attr('value', x.propertyValue);
            input.append(attr);
        });
        if(property.values && property.values[0]) {
            input.val(property.values[0].propertyValue);
        } else {
            input.val('99');
        }

        var wrapper = $('<div />').addClass('formAttributeContent');
        input.attr('disabled', readonly);
        return $('<div />').addClass('formAttributeContentRow').append(label).append(wrapper.append(input));
    };

    var directionChoiceHandler = function(property){
        // TODO: ugliness, remove
        var validityDirection = 2;
        var input = $('<button />').addClass('featureAttributeButton').text('Vaihda suuntaa').click(function(){
            validityDirection = validityDirection == 2 ? 3 : 2;
            triggerEventBusChange(property.publicId, [{ propertyValue: validityDirection }]);
            streetViewHandler.changeDirection(validityDirection);
        });

        //TODO: cleaner html
        var label = $('<div />').addClass('formLabels');
        label.text(property.localizedName);
        if(property.values && property.values[0]) {
            validityDirection = property.values[0].propertyValue;
        }
        var wrapper = $('<div />').addClass('formAttributeContent');
        input.attr('disabled', readonly);
        return $('<div />').addClass('formAttributeContentRow').append(label).append(wrapper.append(input));
    };

    var dateHandler = function(property){
        var input = $('<input />').attr('id', property.publicId).on('keyup datechange', _.debounce(function(target){
            // tab press
            if(target.keyCode === 9){
                return;
            }
            var propertyValue = _.isEmpty(target.currentTarget.value) ? '' : dateutil.finnishToIso8601(target.currentTarget.value);
            triggerEventBusChange(property.publicId, [{ propertyValue: propertyValue }]);
        }, 500));

        //TODO: cleaner html
        var outer = $('<div />').addClass('formAttributeContentRow');

        var label = $('<div />').addClass('formLabels').text(property.localizedName);
        if(property.values[0]) {
            input.val(dateutil.iso8601toFinnish(property.values[0].propertyDisplayValue));
        }
        input.addClass('featureAttributeDate');
        input.attr('disabled', readonly);
        return outer.append(label).append(outer.append($('<div />').addClass('formAttributeContent').append(input)));
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
        var container = $('<div />').addClass('formAttributeContentRow');
        container.append($('<div />').addClass('formLabels').text(property.localizedName));
        var inputContainer = $('<div />').addClass('featureattributeChoice');
        _.forEach(enumValues, function (x) {
            var input = $('<input type="checkbox" />').change(function (evt) {
                x.checked = evt.currentTarget.checked;
                var values = _.chain(enumValues)
                    .filter(function (value) {
                        return value.checked;
                    })
                    .map(function (value) {
                        return { propertyValue: parseInt(value.propertyValue, 10) };
                    })
                    .value();
                if (_.isEmpty(values)) { values.push({ propertyValue: 99 }); }
                triggerEventBusChange(property.publicId, values);
            });
            x.checked = _.any(currentValue.values, function (prop) {
                return prop.propertyValue === x.propertyValue;
            });

            input.prop('checked', x.checked).attr('disabled', readonly);
            var label = $('<label />').text(x.propertyDisplayValue);
            inputContainer.append(input).append(label).append($('<br>'));
        });

        return container.append($('<div />').addClass('formAttributeContent').append(inputContainer));
    };

    var getAssetForm = function(contents) {
        var components =_.map(contents, function(feature){
            feature.localizedName = window.localizedStrings[feature.publicId];
            var propertyType = feature.propertyType;
            if (propertyType === "text" || propertyType === "long_text") {
                return textHandler(feature);
            } else if (propertyType === "read_only_text" || propertyType === 'read-only') {
                return readOnlyHandler(feature);
            } else if (feature.publicId === 'vaikutussuunta') {
                return directionChoiceHandler(feature);
            } else if (propertyType === "single_choice") {
               return singleChoiceHandler(feature, enumeratedPropertyValues);
            } else if (feature.propertyType === "multiple_choice") {
                return multiChoiceHandler(feature, enumeratedPropertyValues);
            } else if (propertyType === "date") {
                 return dateHandler(feature);
            }  else {
                feature.propertyValue = 'Ei toteutettu';
                return $(featureDataTemplateNA(feature));
            }
        });

        return $('<div />').append(components);
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

    var closeAsset = function() {
        $("#featureAttributes").html('');
        dateutil.removeDatePickersFromDom();
        selectedAsset = null;
    };

    eventbus.on('asset:fetched assetPropertyValue:fetched asset:created asset:initialized', function(asset){
        selectedAsset = asset;
        renderAssetForm(asset);
    });

    eventbus.on('asset:unselected', closeAsset);
    eventbus.on('layer:selected', function(layer) {
      activeLayer = layer;
      closeAsset();
    });

    eventbus.on('application:readOnly', function(readOnly) {
        readonly = readOnly;
    });

    eventbus.on('validityPeriod:changed', function(validityPeriods) {
        if (selectedAsset && !_.contains(validityPeriods, selectedAsset.validityPeriod)) {
            closeAsset();
        }
    });

    eventbus.on('enumeratedPropertyValues:fetched', function(values) {
        enumeratedPropertyValues = values;
    });

    eventbus.on('asset:moved', function(position) {
        streetViewHandler.changePosition(position);
    });

    window.Backend.getEnumeratedPropertyValues(10);
})();

