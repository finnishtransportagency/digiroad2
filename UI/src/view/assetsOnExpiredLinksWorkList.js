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

            var trafficDirectionHeader = $('<h2/>').html("Tielinkin liikennevirran suuntaa muutettu");
            var linkTypeHeader = $('<h2/>').html("<br>Tielinkin tyypin muutos vaikuttaa kaistojen lukumäärään");
            var tableContentRows = function (items) {
                var itemsSorted = _.sortBy(items, ["linkId", "createdAt"]);
                return _.map(itemsSorted, function (item) {
                    return $('<tr/>')
                        .append(checkbox(item.id))
                        .append($('<th/>')
                            .append(assetLink(item))
                            .append(changeToLinkInfo(item)));
                });
            };

            var changeToLinkInfo = function (item) {
                var collectionToSearch = item.propertyName === "link_type" ? enumerations.linkTypes : enumerations.trafficDirections;
                var newValueObject = _.find(collectionToSearch, {value: item.newValue});
                var newValueLegend = _.isObject(newValueObject) ? newValueObject.text : 'Tuntematon';
                var oldValueObject = _.find(collectionToSearch, {value: item.oldValue});
                var oldValueLegend = _.isObject(oldValueObject) ? oldValueObject.text : 'Tuntematon';
                return $('<dd class="laneWorkListTextSize"/>')
                    .html("Vanha arvo: " + oldValueLegend + " (" + item.oldValue + ")" +
                        "<br> Uusi arvo: " + newValueLegend + " (" + item.newValue + ")" +
                        "<br> Muokkauksen ajankohta: " + item.createdAt +
                        "<br> Muokkaaja: " + item.createdBy);
            };

            var assetLink = function (item) {
                var link = '#' + "linkProperty" + '/' + item.linkId;
                return $('<a class="work-list-item"/>').attr('href', link).html("Link ID: " + item.linkId);
            };

            var tableForGroupingValues = function (items) {
                if (!items || items.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableContentRows(items))
                    .append('</tbody></table>');
            };

            var checkbox = function (itemId) {
                return $('<td class="laneWorkListCheckboxWidth"/>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
            };

            var deleteBtn = function () {
                return $('<button disabled/>').attr('id', 'deleteAssetOnExpiredLinkItem').addClass('delete btn btn-municipality').text('Poista valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti poistaa valitut kohteet työlistasta?", {
                        container: '#work-list',
                        successCallback: function () {
                            $(".verificationCheckbox:checkbox:checked").each(function () {
                                selectedToDelete.push(parseInt(($(this).attr('value'))));
                            });
                            backend.deleteLaneWorkListItems(selectedToDelete, function () {
                                new GenericConfirmPopup("Valitut kohteet poistettu!", {
                                    container: '#work-list',
                                    type: "alert",
                                    okCallback: function () {
                                        location.reload();
                                    }
                                });
                            }, function () {
                                new GenericConfirmPopup("Valittuja kohteita ei voitu poistaa. Yritä myöhemmin uudelleen!", {
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

            return $('<div/>')
                .append(deleteBtn())
                .append(linkTypeHeader)
                .append(trafficDirectionHeader);
        };
    };
})(this);
