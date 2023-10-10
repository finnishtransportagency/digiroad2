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
            var selectedToDelete = [];
            console.log(workListItems)
            var header = $('<h2/>').html("Samuutuksen muuttaneita linkkejä");
            var tableContentRows = function (item) {
                console.log(item)
                    return $('<tr/>')
                        .append($('<th/>')
                        .append(changeRow(item)));
              
            };

            var changeRow = function (item) {
                return $('<dd class="manoeuvreWorkListTextSize"/>')
                    .html("Rajoituksen id:" +" " + item.assetId + " "+"Rajoituksen linkit:"  + item.links
                    )
            };

            var tableForGroupingValues = function (items) {
                if (!items || items.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableContentRows(items))
                    .append('</tbody></table>');
            };

            return $('<div/>')
                .append(tableForGroupingValues(workListItems));
        };
    };
})(this);
