(function (root) {
    root.MaintenanceFormElements = function (accessRightsValues, maintenanceResponsibilityValues) {
        return elements;

        function elements(unit, editControlLabels, className) {

            return {
                singleValueElement: singleValueElement,
                bindEvents: bindEvents
            };

            function singleValueElement(asset, sideCode) {

                return '' +
                        '<div class="form-group editable ' + generateClassName(sideCode) + '">' +
                        '<label class="asset-label">' + editControlLabels.title + '</label>' +
                        sideCodeMarker(sideCode) +
                       // assetDisplayElement(asset) +
                        assetEditElement(asset) +
                        '</div>';

            }

            function assetEditElement(maintenances) {
                var items = _.map(maintenances, function (maintenance) {
                    return '<li>' + maintenanceEditElement(maintenance) + '</li>';
                }).join('');
                return '' +
                    '<ul class="edit-control-group">' +
                   // items +
                    newMaintenanceElement() +
                    '</ul>';
            }

            function newMaintenanceElement() {
                var accessRightsTags = _.map(accessRightsValues, function (accessRightsValues) {
                    return '<option value="' + accessRightsValues.typeId + '">' + accessRightsValues.title + '</option>';
                }).join('');
                var tag = createSingleChoiceDiv('', accessRightsTags);

                var maintenanceResponsibilityTags = _.map(maintenanceResponsibilityValues, function (maintenanceResponsibilityValues) {
                    return '<option value="' + maintenanceResponsibilityValues.typeId + '">' + maintenanceResponsibilityValues.title + '</option>';
                }).join('');
                tag = createSingleChoiceDiv(tag, maintenanceResponsibilityTags);

                var textValuePropertyNames = ["Tiehoitokunta", "Nimi",'Osoite', 'Postinumero', 'Postitoimipaikka', 'Puhelin 1', 'Puhelin 2', 'Lisätietoa'];
                return tag + _.map(textValuePropertyNames, function (names) {
                    return  '<div class="form-group new-prohibition">' +
                            '<label>' + names + '</label>' +
                            '<div class="form-group new-additionalInfo">' +
                            '<input type="text" class="form-control additional-info"/>' +
                            '</div>' +
                            '</div>';
                }).join('');
            }

            function createSingleChoiceDiv(beginTag, values) {
                return beginTag +  '<li><div class="form-group new-prohibition">' +
                                   '  <select class="form-control select">' +
                                   '    <option class="empty" disabled selected>Valitse arvo</option>' +
                                   values +
                                   '  </select>' +
                                   '</div></li>';
            }

            function maintenanceEditElement(maintenance) {
                function typeElement() {
                    var optionTags = _.map(accessRightsValues, function (accessRightsValues) {
                        var selected = maintenance.typeId === accessRightsValues.typeId ? 'selected' : '';
                        return '<option value="' + accessRightsValues.typeId + '"' + ' ' + selected + '>' + accessRightsValues.title + '</option>';
                    }).join('');
                    return '' +
                        '<div class="form-group prohibition-type">' +
                        '<select class="form-control select">' +
                        optionTags +
                        '</select>' +
                        '</div>';
                }
                return '' +
                    '<div class="form-group existing-prohibition">' +
                    typeElement() +
                    '</div>';
            }

            function sideCodeMarker(sideCode) {
                if (_.isUndefined(sideCode)) {
                    return '';
                } else {
                    return '<span class="marker">' + sideCode + '</span>';
                }
            }

            function generateClassName(sideCode) {
                return sideCode ? className + '-' + sideCode : className;
            }


            function bindEvents(rootElement, selectedLinearAsset, sideCode) {
                var className = '.' + generateClassName(sideCode);
                // eventbus.on('properties:fetched', function (properties) {
                //     var prop = properties;
                // });
           }
        }
    };
})(this);