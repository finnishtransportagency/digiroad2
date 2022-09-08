(function (root) {
    root.LaneWorkList = function () {

        var linkTypes = new Map([
            [1, 'Moottoritie'],
            [2, 'Moniajoratainen tie'],
            [3, 'Yksiajoratainen tie'],
            [4, 'Moottoriliikennetie'],
            [5, 'Kiertoliittymä'],
            [6, 'Ramppi'],
            [7, 'Levähdysalue'],
            [8, 'Kevyen liikenteen väylä'],
            [9, 'Jalankulkualue'],
            [10, 'Huolto- tai pelastustie'],
            [11, 'Liitännäisliikennealue'],
            [12, 'Ajopolku'],
            [13, 'Huoltoaukko'],
            [14, 'Erikoiskuljetusyhteys ilman puomia'],
            [15, 'Erikoiskuljetusyhteys puomilla'],
            [21, 'Lautta/lossi'],
            [22, 'Kaksisuuntainen yksikaistainen tie']
        ]);

        var trafficDirections = new Map([
            [2, 'Molempiin suuntiin'],
            [3, 'Digitointisuuntaa vastaan'],
            [4, 'Digitointisuuntaan']
        ]);

        WorkListView.call(this);
        var me = this;
        var backend;
        this.initialize = function (mapBackend) {
            backend = mapBackend;
            me.bindEvents();
        };

        this.bindEvents = function () {
            eventbus.on('workList-laneModellingTool:select', function (layerName, listP) {
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
                return _.map(items, function (item) {
                    return $('<tr/>')
                        .append(checkbox(item.id))
                        .append($('<th/>')
                        .append(assetLink(item))
                        .append(changeToLinkInfo(item)));
                });
            };

            var changeToLinkInfo = function (item) {
                var newValueLegend = (item.propertyName === "link_type") ? linkTypes.get(item.newValue) : trafficDirections.get(item.newValue);
                var oldValueLegend = (item.propertyName === "link_type") ? linkTypes.get(item.oldValue) : trafficDirections.get(item.oldValue);
                return $('<dd class="work-list-item-description"/>')
                    .html("Vanha arvo: " + oldValueLegend + " (" + item.oldValue + ")" + "<br> Uusi arvo: " + newValueLegend + " (" + item.newValue + ")" + "<br> Muokkauksen ajankohta: " + item.modifiedAt);
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
                return $('<button disabled/>').attr('id', 'deleteUnknownSpeedLimits').addClass('delete btn btn-municipality').text('Poista valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti poistaa valitut tielinkin muutokset työlistasta?", {
                        container: '#work-list',
                        successCallback: function () {
                            $(".verificationCheckbox:checkbox:checked").each(function () {
                                selectedToDelete.push(parseInt(($(this).attr('value'))));
                            });
                            backend.deleteLaneWorkListItems(selectedToDelete, function () {
                                new GenericConfirmPopup("Valitut tarkastettavat tielinkin muutokset poistettu!", {
                                    container: '#work-list',
                                    type: "alert",
                                    okCallback: function () {
                                        location.reload();
                                    }
                                });
                            }, function () {
                                new GenericConfirmPopup("Valittuja tarkastettavia tielinkin muutoksia ei voitu poistaa. Yritä myöhemmin uudelleen!", {
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
                .append(tableForGroupingValues(workListItems.link_type))
                .append(trafficDirectionHeader)
                .append(tableForGroupingValues(workListItems.traffic_direction));
        };
    };
})(this);
