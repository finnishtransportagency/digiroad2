(function(root) {
    root.PopUpAssetLegend = function() {

        var legendDiv = $('<div id="pop-up-map-legend">');

        var assetLegendTemplate =
            '<div class="pop-up-legend-entry">' +
                '<div class="pop-up-label"> Viivamainen kohde </div>' +
                '<div class="pop-up-symbol linear-asset-general" ></div>' +
            '</div>' +
            '<div class="pop-up-legend-entry">' +
            '<div class="pop-up-label">Pistemäinen kohde</div>' +
                '<div class="point-asset-general">' +
                    '<img src="src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg" class="legend-point-asset-icon">' +
                '</div>' +
            '</div>' +
            '<div class="pop-up-legend-entry">' +
                '<div class="pop-up-label"> Poistunut linkki </div>' +
                '<div class="pop-up-symbol linear-expired-link" ></div>' +
            '</div>';


        var expandedTemplate = [
            '<div id="pop-up-legend-box" class="panel pop-up-manoeuvre-box" style="display: block;">',
            '  <header class="panel-header expanded">',
            '    Poistuneet kohteet',
            '  </header>',
            assetLegendTemplate,
            '</div>'
        ].join('');

        legendDiv.append(expandedTemplate);

        return legendDiv;
    };
})(this);
