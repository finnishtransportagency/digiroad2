(function (root) {
    root.AutoProcessedLanesWorkList = function () {

        var enumerations = new Enumerations();

        WorkListView.call(this);
        var me = this;
        var backend;
        this.initialize = function (mapBackend) {
            backend = mapBackend;
            me.bindEvents();
        };

        this.bindEvents = function () {
            eventbus.on('autoProcessedLanesWorkList-laneModellingTool:select', function (layerName, listP) {
                $('.container').hide();
                $('#work-list').show();
                $('body').addClass('scrollable');
                me.generateWorkList(layerName, listP);
            });
        };

        this.workListItemTable = function (layerName, showDeleteCheckboxes, workListItems) {
            var selectedToDelete = [];

            var trafficDirectionHeader = $('<h2></h2>').html("Tielinkin kaistat on päätetty ja generoitu uudelleen liikennöintisuunnan muutoksen seurauksena");
            var linkTypeHeader = $('<h2></h2>').html("<br>Tielinkin kaistat on päätetty ja generoitu uudelleen tielinkin tyypin muutoksen seurauksena");
            var tableContentRows = function (items) {
                var itemsSorted = _.sortBy(items, ["linkId", "createdAt"]);
                return _.map(itemsSorted, function (item) {
                    return $('<tr></tr>')
                        .append(checkbox(item.id))
                        .append($('<th></th>')
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
                var startDatesString = item.startDates.join(', ');
                return $('<dd class="laneWorkListTextSize"></dd>')
                    .html("Vanha arvo: " + oldValueLegend + " (" + item.oldValue + ")" +
                        "<br> Uusi arvo: " + newValueLegend + " (" + item.newValue + ")" +
                        "<br> Muokkauksen ajankohta: " + item.createdAt +
                        "<br> Muokkaaja: " + item.createdBy +
                        "<br> Alkuperäiset alkupäivänmäärät: " + startDatesString);
            };

            var assetLink = function (item) {
                var link = '#' + "linkProperty" + '/' + item.linkId;
                return $('<a class="work-list-item"></a>').attr('href', link).html("Link ID: " + item.linkId);
            };

            var tableForGroupingValues = function (items) {
                if (!items || items.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableContentRows(items))
                    .append('</tbody></table>');
            };

            var checkbox = function (itemId) {
                return $('<td class="laneWorkListCheckboxWidth"></td>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
            };

            var deleteBtn = function () {
                return $('<button disabled></button>').attr('id', 'deleteWorkListItems').addClass('delete btn btn-municipality').text('Poista valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti poistaa valitut tielinkin muutokset työlistasta?", {
                        container: '#work-list',
                        successCallback: function () {
                            $(".verificationCheckbox:checkbox:checked").each(function () {
                                selectedToDelete.push(parseInt(($(this).attr('value'))));
                            });
                            backend.deleteAutoProcessedLanesWorkListItems(selectedToDelete, function () {
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

            return $('<div></div>')
                .append(deleteBtn())
                .append(linkTypeHeader)
                .append(tableForGroupingValues(workListItems.link_type))
                .append(trafficDirectionHeader)
                .append(tableForGroupingValues(workListItems.traffic_direction));
        };
    };
})(this);
