(function (root) {
    root.LaneWorkList = function(){
        WorkListView.call(this);
        var me = this;
        this.initialize = function(){
            me.bindEvents();
        };

        this.bindEvents = function() {
            eventbus.on('workList-laneModellingTool:select', function(layerName, listP) {
                $('.container').hide();
                $('#work-list').show();
                $('body').addClass('scrollable');
                me.generateWorkList(layerName, listP);
            });
        };

        this.workListItemTable = function(layerName, showDeleteCheckboxes, workListItems, municipalityName) {

            var municipalityHeader = function(municipalityName) {
                return $('<h2/>').html("Testi Header");
            };
            var tableHeaderRow = function(headerName) {
                return $('<caption/>').html("Testi caption");
            };
            var tableContentRows = function(Ids) {
                return _.map(Ids, function(item) {
                    return $('<tr/>').append($('<td/>').append(item.assetId ? assetLink(item) : idLink(item)));
                });
            };
            var idLink = function(item) {
                var href =  '#' + layerName + '/linkId/' + item.linkId;
                var link =  '#' + layerName + '/' + item.linkId;
                return $('<a class="work-list-item"/>').attr('href', href).html(link);
            };

            var assetLink = function(item) {
                var link = '#' + layerName + '/' + item.assetId;
                return $('<a class="work-list-item"/>').attr('href', link).html(link);
            };

            var tableForGroupingValues = function(values, Ids) {
                if (!Ids || Ids.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableHeaderRow(values))
                    .append(tableContentRows(Ids))
                    .append('</tbody></table>');
            };

            return $('<div/>').append(municipalityHeader(municipalityName))
                .append(tableForGroupingValues('Kunnan omistama', workListItems.Municipality))
                .append(tableForGroupingValues('Valtion omistama', workListItems.State))
                .append(tableForGroupingValues('Yksityisen omistama', workListItems.Private))
                .append(tableForGroupingValues('Ei tiedossa', workListItems.Unknown));
        };
    };
})(this);
