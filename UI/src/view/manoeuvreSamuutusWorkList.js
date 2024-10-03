(function (root) {
    root.ManoeuvreSamuutusWorkList = function () {

        WorkListView.call(this);
        var me = this;
        var backend;
        this.initialize = function (mapBackend) {
            backend = mapBackend;
            me.bindEvents();
        };

        this.bindEvents = function () {
            eventbus.on('workList-manoeuvreSamuutus:select', function (layerName, listP) {
                $('.container').hide();
                $('#work-list').show();
                $('body').addClass('scrollable');
                me.generateWorkList(layerName, listP);
            });
        };

        this.workListItemTable = function (layerName, showDeleteCheckboxes, workListItems) {
            var tableContentRows = function (item) {
                return $('<tr/>').append($('<th/>').append(changeRow(item)));
            };

            var changeRow = function (item) {
                var idRow = "<p>" + "Rajoituksen id: " + item.assetId + "</p>";
                var linksRow = "<p>" + "Linkit: " + item.links + "</p>";
                var validityPeriodsRow = "<p><b>Validity Periods:</b></p>";
                item.validityPeriods.forEach(function(period) {
                    validityPeriodsRow += "<p>Days: " + period.days.value +
                        ", Time: " + period.startHour + ":" + period.startMinute + " - " +
                        period.endHour + ":" + period.endMinute + "</p>";
                });
                var exceptionTypesRow = "<p><b>Exception Types:</b> " + item.exceptionTypes.join(", ") + "</p>";
                var additionalInfoRow = "<p><b>Additional Info:</b> " + item.additionalInfo + "</p>";
                var createdDateRow = "<p><b>Created Date:</b> " + item.createdDate + "</p>";

                return $('<dd class="manoeuvreWorkListTextSize"/>').html(idRow + linksRow + validityPeriodsRow + exceptionTypesRow + additionalInfoRow + createdDateRow);
            };

            var addTable = function (items) {
                if (!items || items.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableContentRows(items))
                    .append('</tbody></table>');
            };

            return $('<div/>').append(addTable(workListItems));
        };

    };
})(this);
