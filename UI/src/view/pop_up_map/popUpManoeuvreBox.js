(function(root) {
    root.PopUpManoeuvreBox = function() {
        var values = [
            'Ei kääntymisrajoitusta',
            'Kääntymisrajoituksen lähde',
            'Kääntymisrajoituksen lähde, useampi',
            'Kääntymisrajoituksen välilinkki',
            'Kääntymisrajoituksen välilinkki, useampi',
            'Kääntymisrajoituksen kohde',
            'Kääntymisrajoituksen kohde, useampi',
            'Kääntymisrajoituksen lähde ja kohde'
        ];

        var legendDiv = $('<div id="pop-up-map-legend">');

        var manoeuvreLegendTemplate = _.map(values, function(value, idx) {
            return '<div class="pop-up-legend-entry">' +
                '<div class="pop-up-label">' + value + '</div>' +
                '<div class="pop-up-symbol linear-' + idx + '" />' +
                '</div>';
        }).join('');

        manoeuvreLegendTemplate += [
            '  <div id="pop-up-construction-type-legend" class="panel-section panel-legend linear-asset-legend construction-type-legend">',
            '    <div class="legend-entry">',
            '      <div class="pop-up-label">Väliaikaisesti poissa käytöstä (haalennettu linkki)</div>',
            '      <div class="symbol linear construction-type-4"/>',
            '    </div>',
            '  </div>'
        ].join('');

        var expandedTemplate = [
            '<div id="pop-up-legend-box" class="panel pop-up-manoeuvre-box" style="display: block;">',
            '  <header class="panel-header expanded">',
            '    Kääntymisrajoitus',
            '  </header>',
            manoeuvreLegendTemplate,
            '</div>'
        ].join('');

        legendDiv.append(expandedTemplate);

        return legendDiv;
    };
})(this);
