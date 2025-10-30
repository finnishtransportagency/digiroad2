(function (root) {
    root.AssetsOnExpiredLinksWorkList = function () {

        var enumerations = new Enumerations();

        WorkListView.call(this);
        var me = this;
        var backend;
        this.initialize = function (mapBackend) {
            backend = mapBackend;
            me.bindEvents();
        };

        this.bindEvents = function () {
            eventbus.on('workList-assetsOnExpiredLinks:select', function (layerName, listP) {
                $('.container').hide();
                $('#work-list').show();
                $('body').addClass('scrollable');
                me.generateWorkList(layerName, listP);
            });
        };

        this.workListItemTable = function (layerName, showDeleteCheckboxes, workListItems) {
            var selectedToDelete = [];

            var tableForGroupingValues = function (groupedItems) {
                if (!groupedItems || groupedItems.length === 0) return '';

                var table = $('<table>').addClass('table');
                var tbody = $('<tbody>');

                // Iterate through the grouped items and create a assetType header row and legend row for each group
                _.each(groupedItems, function (group, assetTypeId) {
                    var assetTypeName = _.find(enumerations.assetTypes, function(assetType) {
                        return assetType.typeId === parseInt(assetTypeId, 10);
                    }).nameFI;
                    var headerRow = $('<tr>').addClass('group-header');
                    var headerCell = $('<th>').attr('colspan', '9').text(assetTypeName);
                    headerRow.append(headerCell);
                    tbody.append(headerRow);

                    var legendRow = $('<tr>').addClass('group-legend');
                    var legendCells = [
                        $('<td>'),
                        $('<td>').text(assetTypeName === enumerations.assetTypes.MassTransitStopAsset.nameFI ? "National ID" : "ID"),
                        $('<td>').text("LinkID"),
                        $('<td>').text("SideCode"),
                        $('<td>').text("StartM"),
                        $('<td>').text("EndM"),
                        $('<td>').text("Tielinkin päättymispvm."),
                        $('<td>').text(""),
                        $('<td>').text("")
                    ];

                    legendRow.append(legendCells);
                    tbody.append(legendRow);

                    // Iterate through the items in the group and create a row for each item
                    _.each(group, function (item) {
                        var row = $('<tr>');
                        var firstPoint = _.head(item.geometry);
                        var lastPoint;
                        var divider = "";
                        if (item.geometry.length === 1) {
                            lastPoint = {
                                x: "",
                                y: ""
                            };
                        } else {
                            lastPoint = _.last(item.geometry);
                            divider = ", ";
                        }

                        var cells = [
                            $('<td>').append(checkbox(item.id)),
                            $('<td>').text(item.nationalId ? item.nationalId : item.id),
                            $('<td>').text(item.linkId),
                            $('<td>').text(item.sideCode),
                            $('<td>').text(item.startMeasure),
                            $('<td>').text(item.endMeasure),
                            $('<td>').text(item.roadLinkExpiredDate),
                            $('<td>').append(openMapButton(item),copyAdditionalInfoButton(item)),
                        ];

                        row.append(cells);
                        tbody.append(row);
                    });
                });

                table.append(tbody);
                return table;
            };

            var checkbox = function (itemId) {
                return $('<td class="laneWorkListCheckboxWidth"></td>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
            };

            var deleteBtn = function () {
                return $('<button disabled></button>').attr('id', 'deleteWorkListItems').addClass('delete btn btn-municipality').text('Päätä valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti päättää valitut kohteet ja poistaa ne työlistalta?", {
                        container: '#work-list',
                        successCallback: function () {
                            $(".verificationCheckbox:checkbox:checked").each(function () {
                                selectedToDelete.push(parseInt(($(this).attr('value'))));
                            });
                            backend.deleteAssetsOnExpiredLinksWorkListItems(selectedToDelete, function () {
                                new GenericConfirmPopup("Valitut kohteet päätetty ja poistettu työlistalta!", {
                                    container: '#work-list',
                                    type: "alert",
                                    okCallback: function () {
                                        location.reload();
                                    }
                                });
                            }, function () {
                                new GenericConfirmPopup("Valittuja kohteita ei voitu päättää ja poistaa työlistalta. Yritä myöhemmin uudelleen!", {
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

            var openMapButton = function (item) {
                return $('<button/>')
                    .addClass('btn btn-municipality')
                    .text('Avaa kartalla')
                    .css('margin', '5px')
                    .click(function () {
                        new WorkListPopUpMap(backend, item, "assetsOnExpiredLinksWorkList");
                    });
            };

            var copyAdditionalInfoButton = function (item) {
                return $('<button/>')
                    .addClass('btn btn-municipality')
                    .text('Kopioi ominaisuustiedot')
                    .css('margin', '5px')
                    .click(function () {
                        var button = $(this);
                        var textToCopy = JSON.stringify(item.additionalData, null, 2);

                        if (navigator.clipboard && navigator.clipboard.writeText) {
                            navigator.clipboard.writeText(textToCopy)
                                .then(function() {
                                showNotification('success', 'Ominaisuustiedot kopioitu leikepöydälle!', button);
                            })
                                .catch(function(err) {
                                    showNotification('failure', 'Kopiointi epäonnistui!', button);
                                });
                        } else {
                            alert('Kopiointia leikepöydälle ei tueta tässä selaimessa.');
                        }
                    });
            };

            function showNotification(type, message, button) {
                var notification = $('<div class="pop-up-copy-notification ' + type + '">')
                    .text(message)
                    .css({
                        top: button.offset().top - 3 + 'px',
                        left: button.offset().left + 140 + 'px'
                    });

                $('body').append(notification);
                setTimeout(function() {
                    notification.css('opacity', '1');
                }, 10);
                setTimeout(function() {
                    notification.fadeOut(300, function() {
                        notification.remove();
                    });
                }, 2000);
            }

            return $('<div></div>')
                .append(deleteBtn())
                .append(tableForGroupingValues(workListItems));
        };
    };
})(this);
