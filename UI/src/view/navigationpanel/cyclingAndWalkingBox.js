(function(root) {
    root.CyclingAndWalkingBox = function (assetConfig) {
        LinearAssetBox.call(this, assetConfig);
        var me = this;

        this.legendName = function () {
            return 'linear-asset-legend cycling-and-walking';
        };

        var auxLegend =  [
            {index: 1, text: 'Pyöräily ja kävely kielletty' },
            {index: 2, text: 'Pyöräily kielletty' },
            {index: 3, text: 'Jalankulun ja pyöräilyn väylä' },
            {index: 4, text: 'Katu' },
            {index: 5, text: 'Maantie tai yksityistie' },
            {index: 6, text: 'Pyöräkatu'},
            {index: 7, text: 'Kylätie'},
            {index: 8, text: 'Pihakatu'},
            {index: 9, text: 'Kävelykatu'},
            {index: 10, text: 'Pyöräkaista'},
            {index: 11, text: 'Pyörätie'},
            {index: 12, text: 'Kaksisuuntainen pyörätie'},
            {index: 13, text: 'Yhdistetty pyörätie ja jalkakäytävä, yksisuuntainen pyörille'},
            {index: 14, text: 'Yhdistetty pyörätie ja jalkakäytävä, kaksisuuntainen pyörille'},
            {index: 15, text: 'Puistokäytävä'},
            {index: 16, text: 'Jalkakäytävä'},
            {index: 17, text: 'Pururata'},
            {index: 18, text: 'Ajopolku'},
            {index: 19, text: 'Polku'},
            {index: 20, text: 'Lossi tai lautta'}
        ];

        var constructionTypeLegend = '<div class="panel-section panel-legend linear-asset-legend construction-type-legend">';
        var constructionTypes = [
            {index: 1, text: 'Rakenteilla' }, //Under Construction
            {index: 3, text: 'Suunnitteilla' } //Planned
        ];



        this.labeling = function () {
            var legend =  _.map(auxLegend, function(legend) {
                return '<div class="legend-entry">' +
                    '<div class="label">' + legend.text + '</div>' +
                    '<div class="symbol linear cycling-and-walking-' + legend.index + '" />' +
                    '</div>';
            }).join('')+' </div>';

            var constructionTypeLegendEntries = _.map(constructionTypes, function(constructionType) {
                return '<div class="legend-entry">' +
                    '<div class="label">' + constructionType.text + '</div>' +
                    '<div class="symbol linear construction-type-' + constructionType.index + '" />' +
                    '</div>';
            }).join('')+ '</div>';

            constructionTypeLegend = constructionTypeLegend.concat(constructionTypeLegendEntries);

            return legend + constructionTypeLegend;
        };

        this.predicate = function () {
            return (!assetConfig.readOnly && assetConfig.authorizationPolicy.editModeAccess());
        };

        this.editModeToggle = new EditModeToggleButton(me.toolSelection);

        var element = $('<div class="panel-group cycling-and-walking"/>');

        function show() {
            if (assetConfig.authorizationPolicy.editModeAccess()) {
                me.editModeToggle.reset();
            } else {
                me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
            }
            element.show();
        }

        function hide() {
            element.hide();
        }

        this.getElement = function () {
            return element;
        };

        this.show = show;
        this.hide = hide;
    };
})(this);
