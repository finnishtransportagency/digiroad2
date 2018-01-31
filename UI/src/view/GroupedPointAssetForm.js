(function (root) {
    root.GroupedPointAssetForm = {
        initialize: bindEvents
    };

    var enumeratedPropertyValues = null;

    function bindEvents(typeIds, selectedAsset, collection, layerName, localizedTexts, editConstrains, roadCollection, applicationModel, backend) {
        var rootElement = $('#feature-attributes');

        //backend.getAssetEnumeratedPropertyValues(typeId);

        eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function () {
            if (!_.isEmpty(roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
                renderForm(rootElement, selectedAsset, localizedTexts, editConstrains, roadCollection, collection, typeIds);
            }
        });

        eventbus.on('assetEnumeratedPropertyValues:fetched', function(event) {
            if(event.assetType == typeId)
                enumeratedPropertyValues = event.enumeratedPropertyValues;
        });

        eventbus.on(layerName + ':unselected', function() {
            rootElement.empty();
        });
    }

    function renderForm(rootElement, selectedAsset, localizedTexts, editConstrains, roadCollection, collection, typeIds) {
        var id = selectedAsset.getId();

        var title = localizedTexts.title;
        var header = '<header><span>' + title + '</span></header>';
        var form = '';
        _.forEach(typeIds, function(typeId) {
            form += renderAssetFormElements(selectedAsset, localizedTexts, collection, typeId);
        });

        rootElement.html(header + '<div class="wrapper">' + form + '</div>');
    }

    function renderAssetFormElements(selectedAsset, localizedTexts, collection, typeId) {
        var asset = selectedAsset.get();
        return '' +
            '  <div class="form form-horizontal form-dark form-pointasset">' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-type-info">' + weightLimitsTypes[typeId] + '</p>' +
            '    </div>' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
            '    </div>' +
            '    <div class="form-group">' +
            '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
            '    </div>' +
            //renderValueElement(asset, collection) +
            '  </div>';
    }

    var safetyEquipments = {
        1: 'Rautatie ei käytössä',
        2: 'Ei turvalaitetta',
        3: 'Valo/äänimerkki',
        4: 'Puolipuomi',
        5: 'Kokopuomi'
    };

    var weightLimitsTypes = {
        320: 'SUURIN SALLITTU MASSA',
        330: 'YHDISTELMÄN SUURIN SALLITTU MASSA',
        340: 'SUURIN SALLITTU AKSELIMASSA',
        350: 'SUURIN SALLITTU TELIMASSA'
    };

    //var textHandler = function (property) {
    //    var propertyValue = (property.values.length === 0) ? '' : property.values[0].propertyValue;
    //    return '' +
    //        '    <div class="form-group editable form-traffic-sign">' +
    //        '        <label class="control-label">' + property.localizedName + '</label>' +
    //        '        <p class="form-control-static">' + (propertyValue || '–') + '</p>' +
    //        '        <input type="text" class="form-control" id="' + property.publicId + '" value="' + propertyValue + '">' +
    //        '    </div>';
    //};

    function renderValueElement(asset) {
         if (asset.safetyEquipment) {
            return '' +
                '    <div class="form-group editable form-railway-crossing">' +
                '      <label class="control-label">Turvavarustus</label>' +
                '      <p class="form-control-static">' + safetyEquipments[asset.safetyEquipment] + '</p>' +
                '      <select class="form-control" style="display:none">  ' +
                '        <option value="1" '+ (asset.safetyEquipment === 1 ? 'selected' : '') +'>Rautatie ei käytössä</option>' +
                '        <option value="2" '+ (asset.safetyEquipment === 2 ? 'selected' : '') +'>Ei turvalaitetta</option>' +
                '        <option value="3" '+ (asset.safetyEquipment === 3 ? 'selected' : '') +'>Valo/äänimerkki</option>' +
                '        <option value="4" '+ (asset.safetyEquipment === 4 ? 'selected' : '') +'>Puolipuomi</option>' +
                '        <option value="5" '+ (asset.safetyEquipment === 5 ? 'selected' : '') +'>Kokopuomi</option>' +
                '      </select>' +
                '    </div>' +
                '    <div class="form-group editable form-railway-crossing">' +
                '        <label class="control-label">' + 'Nimi' + '</label>' +
                '        <p class="form-control-static">' + (asset.name || '–') + '</p>' +
                '    </div>';
        } else {
            return '';
        }
    }
})(this);
