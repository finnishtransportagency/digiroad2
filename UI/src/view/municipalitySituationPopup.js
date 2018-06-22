window.MunicipalitySituationPopup = function (models) {
    var me = this;
    var assetConfig = new AssetTypeConfiguration();

    this.initialize = function () {
        eventbus.on('verificationInfoCriticalAssets:fetched', function (result) {
            renderDialog(result);
        });

        models.fetchVerificationInfoCriticalAssets(20);
    };

    var options = {
        message: 'Tietolajien päivitystilanne: Huittinen',
        saveButton: 'Siirry tietolajien kuntasivulle',
        cancelButton: 'Sulje',
        saveCallback: function(){},
        cancelCallback: function(){},
        closeCallback: function() { purge(); }
    };

    var purge = function() {
        $('.confirm-modal').remove();
    };

    var renameAssets = function (values) {
        _.forEach(values, function (asset) {
            asset.assetName = _.find(assetConfig.assetTypeInfo, function(config){ return config.typeId ===  asset.typeId; }).title ;
        });
    };

    this.createCriticalAssetsVerificationInfoForm = function (verificationsInfo) {
        var sortAssets = function (values) {
            var assetOrdering = [
                'Nopeusrajoitus',
                'Joukkoliikenteen pysäkki',
                'Kääntymisrajoitus',
                'Ajoneuvokohtaiset rajoitukset',
                'Suurin sallittu massa'
            ];

            return _.sortBy(values, function(property) {
                return _.indexOf(assetOrdering, property.assetName);
            });
        };

        var verificationTableHeaderRow = function () {
            return '<thead><th id="name">TIETOLAJI</th> <th id="date">TARKISTETTU</th> <th id="verifier">K-TUNNUS</th></thead>';
        };

        var verificationTableContentRows = function (values) {
            renameAssets(values);
            values = _.first(sortAssets(values));
            return "<tbody>" +
                _.map(values, function (asset) {
                    return '' +
                        "<tr>" +
                        "<td headers='name'>" + asset.assetName + "</td>" +
                        "<td headers='date'>" + asset.verified_date + "</td>" +
                        "<td headers='verifier'>" + asset.verified_by + "</td>" +
                        "</tr>";
                }).join("</tbody>");
        };

        var tableForGroupingValues = function (values) {
            return '' +
                '<table>' +
                    verificationTableHeaderRow() +
                    verificationTableContentRows(values) +
                '</table>';
        };

        return '<div id="dashBoardVerificationInfo">' + tableForGroupingValues(verificationsInfo) + '</div>';
    };

    var renderDialog = function(verificationsInfo) {
        $('#work-list').append(me.createMunicipalitySituationPopUp(verificationsInfo)).show();

        $('.confirm-modal .cancel').on('click', function() {
            options.cancelCallback();
        });
        $('.confirm-modal .save').on('click', function() {
            options.saveCallback();
        });

        $(' .confirm-modal .sulje').on('click', function() {
            options.closeCallback();
        });
    };

    this.createMunicipalitySituationPopUp = function (verificationsInfo) {
        return '' +
            '<div class="modal-overlay confirm-modal" id="municipalitySituation">' +
                '<div class="modal-dialog municipalitySituation">' +
                    '<div class="content">' + options.message + '<a class="header-link sulje"">Sulje</a>' + '</div>' +
                        '<label class="control-label" id="title">Tarkistetut tietolajit</label>' +
                         me.createCriticalAssetsVerificationInfoForm(verificationsInfo) +
                    '<div class="actions">' +
                        '<button class = "btn btn-primary save" disabled>' + options.saveButton + '</button>' +
                        '<button class = "btn btn-secondary cancel">' + options.cancelButton + '</button>' +
                    '</div>' +
                '</div>' +
            '</div>';
    };
};