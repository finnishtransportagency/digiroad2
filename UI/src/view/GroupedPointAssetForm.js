(function (root) {
    root.GroupedPointAssetForm = {
        initialize: bindEvents
    };

    function bindEvents(typeIds, selectedAsset, layerName, localizedTexts, roadCollection, propertiesData) {
        var rootElement = $('#feature-attributes');

        eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function () {
            if (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
                renderForm(rootElement, selectedAsset, localizedTexts, typeIds, propertiesData);
            }
        });

        eventbus.on(layerName + ':unselected', function() {
            rootElement.empty();
        });
    }

    function renderForm(rootElement, selectedAsset, localizedTexts, typeIds, propertiesData) {
        var title = localizedTexts.title;
        var header = '<header><span>' + title + '</span></header>';
        var form = '';
        var propertyData;

        _.forEach(typeIds, function(typeId) {
            propertyData = _.filter(propertiesData, function (property) {
                return property.propertyTypeId == typeId;
            });

            form += renderAssetFormElements(selectedAsset, typeId, propertyData);
        });

        rootElement.html(header + '<div class="wrapper">' + form + '</div>');
    }

    function renderAssetFormElements(selectedAsset, typeId, propertyData) {
        var asset = _.filter(selectedAsset.get().assets, function (assets) {
            return assets.typeId == typeId;
        });

        return '' +
            '  <div class="form form-horizontal form-dark form-pointasset">' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-type-info">' + weightLimitsTypes[typeId] + '</p>' +
            '      <p class="form-control-static asset-type-info">' + ('ID: ' + asset[0].id || '') + '</p>' +
            '    </div>' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset[0].createdBy || '-') + ' ' + (asset[0].createdAt || '') + '</p>' +
            '    </div>' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset[0].modifiedBy || '-') + ' ' + (asset[0].modifiedAt || '') + '</p>' +
            '    </div>' +
            renderValueElement(asset[0], propertyData) +
            '  </div>';
    }

    var weightLimitsTypes = {
        320: 'SUURIN SALLITTU MASSA',
        330: 'YHDISTELMÄN SUURIN SALLITTU MASSA',
        340: 'SUURIN SALLITTU AKSELIMASSA',
        350: 'SUURIN SALLITTU TELIMASSA'
    };

    var numberHandler = function (value, property) {
        return '' +
            '    <div class="form-group editable form-grouped-point">' +
            '        <label class="control-label-grouped-point">' + property.localizedName + '</label>' +
            '        <p class="form-control-static-grouped-point">' + ( (value > 0) ? (value + ' kg') : '-') + '</p>' +
            '    </div>';
    };

    function renderValueElement(asset, propertyData) {
        var allGroupedPointAssetProperties = propertyData;
        var components = _.reduce(_.map(allGroupedPointAssetProperties, function (feature) {
            feature.localizedName = window.localizedStrings[feature.publicId];
            var propertyType = feature.propertyType;

            if (propertyType === "number")
                return numberHandler(asset.limit, feature);

        }), function(prev, curr) { return prev + curr; }, '');

        return components;
    }
})(this);
