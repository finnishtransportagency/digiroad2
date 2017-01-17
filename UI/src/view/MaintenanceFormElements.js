(function (root) {
    root.MaintenanceFormElements = function (accessRightsValues, maintenanceResponsibilityValues) {
        return elements;

        function elements(unit, editControlLabels, className, properties) {

            return {
                singleValueElement: singleValueElement,
                bindEvents: bindEvents
            };

            function singleValueElement(asset, sideCode) {

                return '' +
                    '<div class="form-group editable ' + generateClassName(sideCode) + '">' +
                    '<label class="asset-label">' + editControlLabels.title + '</label>' +
                    sideCodeMarker(sideCode) +
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
                eventbus.on('properties:fetched', function (properties) {
                    var prop = properties;
                });
           }
        }
    };
})(this);