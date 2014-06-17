(function() {
    var enumeratedPropertyValues = null;
    var readOnly = true;
    var streetViewHandler;
    var activeLayer = 'asset';

    _.templateSettings = {
        interpolate: /\{\{(.+?)\}\}/g
    };

    var renderAssetForm = function() {
        if (activeLayer !== 'asset') {
            return;
        }
        var container = $("#feature-attributes").empty();

        var element = $('<header />').html(busStopHeader());
        var wrapper = $('<div />').addClass('wrapper');
        streetViewHandler = getStreetView();
        wrapper.append(streetViewHandler.render()).append($('<div />').addClass('form form-horizontal form-dark').attr('role', 'form').append(getAssetForm()));
        var featureAttributesElement = container.append(element).append(wrapper);
        addDatePickers();

        var cancelBtn = $('<button />').addClass('cancel btn btn-secondary').text('Peruuta').click(function() {
            $("#feature-attributes").empty();
            selectedAssetModel.cancel();
        });

        var saveBtn = $('<button />').addClass('save btn btn-primary').text('Tallenna').click(function() {
            selectedAssetModel.save();
        });

        // TODO: cleaner html
        featureAttributesElement.append($('<footer />').addClass('form-controls').append(saveBtn).append(cancelBtn));

        if (readOnly) {
            $('#feature-attributes .form-controls').hide();
            wrapper.addClass('read-only');
        }

        function busStopHeader(asset) {
            if (_.isNumber(selectedAssetModel.get('externalId'))) {
                return 'Valtakunnallinen ID: ' + selectedAssetModel.get('externalId');
            }
            else return 'Uusi pys&auml;kki';
        }
    };

    var getStreetView = function() {
        var model = selectedAssetModel;
        var render = function() {
            var wgs84 = OpenLayers.Projection.transform(
                new OpenLayers.Geometry.Point(model.get('lon'), model.get('lat')),
                new OpenLayers.Projection('EPSG:3067'), new OpenLayers.Projection('EPSG:4326'));
            return $(streetViewTemplate({
              wgs84X: wgs84.x,
              wgs84Y: wgs84.y,
              heading: (model.get('validityDirection') === 3 ? model.get('bearing') - 90 : model.get('bearing') + 90),
              protocol: location.protocol
            })).addClass('street-view');
        };

        var update = function(){
            $('.street-view').empty().append(render());
        };

        return {
            render: render,
            update: update
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
        var outer = $('<div />').addClass('form-group');
        var propertyVal = _.isEmpty(property.values) === false ? property.values[0].propertyDisplayValue : '';
        // TODO: use cleaner html
        if (property.propertyType === 'read_only_text') {
            outer.append($('<p />').addClass('form-control-static asset-log-info').text(property.localizedName + ': ' + propertyVal));
        } else {
            outer.append($('<label />').addClass('control-label').text(property.localizedName));
            outer.append($('<p />').addClass('form-control-static').text(propertyVal));
        }
        return outer;
    };

    var textHandler = function(property){
        var inputElement = property.propertyType === 'long_text' ?
                $('<textarea />').addClass('form-control') : $('<input type="text"/>').addClass('form-control');
        var input = inputElement.bind('input', function(target){
            selectedAssetModel.setProperty(property.publicId, [{ propertyValue: target.currentTarget.value }]);
        });

        // TODO: use cleaner html
        var outer = $('<div />').addClass('form-group').attr('data-required', property.required);
        outer.append($('<label />').addClass('control-label').text(property.localizedName));

        outer.append(input);
        if(property.values[0]) {
            input.val(property.values[0].propertyDisplayValue);
        }
        input.attr('disabled', readOnly);
        return outer;
    };

    var singleChoiceHandler = function(property, choices){
        var enumValues = _.find(choices, function(choice){
            return choice.publicId === property.publicId;
        }).values;

        var input = $('<select />').addClass('form-control').change(function(x){
            selectedAssetModel.setProperty(property.publicId, [{ propertyValue: x.currentTarget.value }]);
        });

        //TODO: cleaner html
        var label = $('<label />').addClass('control-label');
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

        input.attr('disabled', readOnly);
        return $('<div />').addClass('form-group').append(label).append(input);
    };

    var directionChoiceHandler = function(property){
        var input = $('<button />').addClass('btn btn-secondary btn-block').text('Vaihda suuntaa').click(function(){
            selectedAssetModel.switchDirection();
            streetViewHandler.update();
        });

        //TODO: cleaner html
        var label = $('<label />').addClass('control-label');
        label.text(property.localizedName);
        if(property.values && property.values[0]) {
            validityDirection = property.values[0].propertyValue;
        }

        input.attr('disabled', readOnly);
        return $('<div />').addClass('form-group').append(label).append(input);
    };

    var dateHandler = function(property){
        var input = $('<input />').addClass('form-control').attr('id', property.publicId).on('keyup datechange', _.debounce(function(target){
            // tab press
            if(target.keyCode === 9){
                return;
            }
            var propertyValue = _.isEmpty(target.currentTarget.value) ? '' : dateutil.finnishToIso8601(target.currentTarget.value);
            selectedAssetModel.setProperty(property.publicId, [{ propertyValue: propertyValue }]);
        }, 500));

        //TODO: cleaner html
        var outer = $('<div />').addClass('form-group');

        var label = $('<label />').addClass('control-label').text(property.localizedName);
        if(property.values[0]) {
            input.val(dateutil.iso8601toFinnish(property.values[0].propertyDisplayValue));
        }
        input.attr('disabled', readOnly);
        return outer.append(label).append(input);
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
        var container = $('<div />').addClass('form-group');
        container.append($('<label />').addClass('control-label').text(property.localizedName));
        var inputContainer = $('<div />').addClass('choice-group');
        _.forEach(enumValues, function (x) {
            var outer = $('<div class="checkbox" />');
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
                selectedAssetModel.setProperty(property.publicId, values);
            });
            x.checked = _.any(currentValue.values, function (prop) {
                return prop.propertyValue === x.propertyValue;
            });

            input.prop('checked', x.checked).attr('disabled', readOnly);
            if (readOnly) {
                outer.addClass('disabled');
            }

            var label = $('<label />').text(x.propertyDisplayValue);
            inputContainer.append(outer.append(label.append(input)));
        });

        return container.append(inputContainer);
    };

    var getAssetForm = function() {
        var contents = selectedAssetModel.getProperties();
        var components =_.map(contents, function(feature){
            feature.localizedName = window.localizedStrings[feature.publicId];
            var propertyType = feature.propertyType;
            if (propertyType === "text" || propertyType === "long_text") {
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
            '<a target="_blank" href="{{protocol}}//maps.google.com/?ll={{wgs84Y}},{{wgs84X}}&cbll={{wgs84Y}},{{wgs84X}}&cbp=12,{{heading}}.09,,0,5&layer=c&t=m">' +
            '<img alt="Google StreetView-n&auml;kym&auml;" src="http://maps.googleapis.com/maps/api/streetview?key=AIzaSyBh5EvtzXZ1vVLLyJ4kxKhVRhNAq-_eobY&size=360x180&location={{wgs84Y}}' +
            ', {{wgs84X}}&fov=110&heading={{heading}}&pitch=-10&sensor=false">' +
            '</a>');

    var featureDataTemplateNA = _.template('<div class="formAttributeContentRow">' +
        '<div class="formLabels">{{localizedName}}</div>' +
        '<div class="featureAttributeNA">{{propertyValue}}</div>' +
        '</div>');

    var closeAsset = function() {
        $("#feature-attributes").html('');
        dateutil.removeDatePickersFromDom();
    };

    eventbus.on('asset:modified', function(asset){
        renderAssetForm();
    });

    eventbus.on('layer:selected', function(layer) {
      activeLayer = layer;
      closeAsset();
    });

    eventbus.on('application:readOnly', function(data) {
        readOnly = data;
    });

    eventbus.on('asset:closed', closeAsset);

    eventbus.on('enumeratedPropertyValues:fetched', function(values) {
        enumeratedPropertyValues = values;
    });

    eventbus.on('asset:moved', function() {
        streetViewHandler.update();
    });

    window.Backend.getEnumeratedPropertyValues(10);
})();

