(function (root) {
    root.RoadLinkReplacementWorkList = function () {

        WorkListView.call(this);
        var me = this;
        var backend;
        this.initialize = function (mapBackend) {
            backend = mapBackend;
            me.bindEvents();
        };

        this.bindEvents = function () {
            eventbus.on('roadLinkReplacementWorkList-linkProperty:select', function (layerName, listP) {
                $('.container').hide();
                $('#work-list').show();
                $('body').addClass('scrollable');
                me.generateWorkList(layerName, listP);
            });
        };

        this.workListItemTable = function (layerName, showDeleteCheckboxes, workListItems) {
            var selectedToDelete = [];
            var tableContentRows = function (items) {
                var itemsSorted = _.sortBy(items, ["linkId", "createdAt"]);
                return _.map(itemsSorted, function (item) {
                    return $('<tr/>')
                        .append(checkbox(item.id))
                        .append($('<th/>')
                        .append(removedAndAddedLinkIds(item)));
                });
            };

            var removedAndAddedLinkIds = function (item) {
                return $('<dd class="roadLinkReplacementWorkListTextSize"/>')
                    .html("Poistetun tielinkin ID: " + item.removedLinkId +
                        "<br> Lisätyn tielinkin ID: " + item.addedLinkId);
            };

            var checkbox = function (itemId) {
                return $('<td class="roadLinkReplacementCheckboxWidth"/>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
            };

            var deleteBtn = function () {
                return $('<button disabled/>').attr('id', 'deleteWorkListItems').addClass('delete btn btn-municipality').text('Poista valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti poistaa valitut tielinkki parit työlistasta?", {
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

            return $('<div/>')
                .append(deleteBtn())
                .append(tableContentRows(workListItems));
        };
    };
})(this);
