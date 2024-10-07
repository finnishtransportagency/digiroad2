(function (root) {
    root.ManoeuvreSamuutusWorkList = function () {

        WorkListView.call(this);
        var me = this;
        var backend;
        var enumerations = new Enumerations();
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
            var selectedToDelete = [];
            var tableContentRows = function (items) {
                return items.map(function (item) {
                    return $('<tr/>')
                        .append(checkbox(item.assetId))
                        .append($('<td/>').html(changeRow(item)))
                        .append($('<td/>').append(openMapButton(item)));
                });
            };

            var changeRow = function (item) {
                var exceptionTypesEnumerations = item.exceptionTypes.map(function (typeId) {
                    var exception = enumerations.manoeuvreExceptions.find(function (exception) {
                        return exception.typeId === typeId;
                    });
                    return exception ? exception.title : "Unknown Type";
                });

                var idRow = "<p><strong>Kääntymisrajoituksen ID:</strong> " + item.assetId + "</p>";
                var linksRow = "<p><strong>Tielinkit:</strong> " + item.links + "</p>";
                var validityPeriodsRow = "<p><strong>Voimassaoloajat:</strong></p><ul>";

                item.validityPeriods.forEach(function (period) {
                    var dayEnumeration = enumerations.manoeuvreValidityPeriodDays.find(function (day) {
                        return day.value === period.days.value;
                    });
                    validityPeriodsRow += "<li>Viikonpäivät: " + dayEnumeration.title + ", Kellonaika: " +
                        period.startHour + ":" + period.startMinute + " - " +
                        period.endHour + ":" + period.endMinute + "</li>";
                });

                validityPeriodsRow += "</ul>";
                var exceptionTypesRow = "<p><strong>Rajoitus ei koske seuraavia tyyppejä:</strong> " + exceptionTypesEnumerations.join(", ") + "</p>";
                var additionalInfoRow = "<p><strong>Muu tarkenne:</strong> " + item.additionalInfo + "</p>";
                var createdDateRow = "<p><strong>Työlistakohteen luontipäivänmäärä:</strong> " + item.createdDate + "</p>";

                return $('<div/>').html(idRow + linksRow + validityPeriodsRow + exceptionTypesRow + additionalInfoRow + createdDateRow);
            };

            var checkbox = function (itemId) {
                return $('<td class="manoeuvreWorkListCheckboxWidth"/>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
            };

            var openMapButton = function (item) {
                return $('<button/>')
                    .addClass('delete btn btn-municipality')
                    .text('Avaa kartalla')
                    .click(function () {
                        new WorkListPopUpMap(backend, item);
                    });
            };

            var deleteBtn = function () {
                return $('<button disabled/>').attr('id', 'deleteWorkListItems').addClass('delete btn btn-municipality').text('Poista valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti poistaa valitut kääntymisrajoitukset työlistasta?", {
                        container: '#work-list',
                        successCallback: function () {
                            $(".verificationCheckbox:checkbox:checked").each(function () {
                                selectedToDelete.push(parseInt(($(this).attr('value'))));
                            });
                            backend.deleteManoeuvresWorkListItems(selectedToDelete, function () {
                                new GenericConfirmPopup("Valitut kääntymisrajoitukset poistettu työlistalta!", {
                                    container: '#work-list',
                                    type: "alert",
                                    okCallback: function () {
                                        location.reload();
                                    }
                                });
                            }, function () {
                                new GenericConfirmPopup("Valittuja kääntymisrajoituksia ei voitu poistaa työlistalta. Yritä myöhemmin uudelleen!", {
                                    container: '#work-list',
                                    type: "alert"
                                });
                            });
                            selectedToDelete = [];
                        },
                        closeCallback: function () {
                        }
                    });
                });
            };

            var addTable = function (manoeuvreWorkListItems) {
                if (!manoeuvreWorkListItems || manoeuvreWorkListItems.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableContentRows(manoeuvreWorkListItems))
                    .append('</tbody></table>');
            };

            return $('<div/>')
                .append(deleteBtn())
                .append(addTable(workListItems));
        };


    };
})(this);
