window.MunicipalitySituationPopup = function (models) {
    var me = this;
    var assetConfig = new AssetTypeConfiguration();

    this.initialize = function () {
        eventbus.on('dashBoardInfoAssets:fetched', function (results) {
            if (!_.isEmpty(results)) {
              var verifyInfos = results._1;
              var modifyInfos = results._2;
              var totalSuggestedAssets = results._3;
              renderDialog(verifyInfos, modifyInfos, totalSuggestedAssets);
            }
        });

        models.fetchDashBoardInfo();
    };

    var options = {
        message: 'Tietolajien päivitystilanne',
        saveButton: 'Siirry tietolajien kuntasivulle',
        cancelButton: 'Sulje',
        saveCallback: function(){},
        cancelCallback: function() { purge(); },
        closeCallback: function() { purge(); }
    };

    var purge = function() {
        $('.confirm-modal#municipalitySituation').remove();
    };

    var renameAssets = function (values) {
        _.forEach(values, function (asset) {
            asset.assetName = _.find(assetConfig.assetTypeInfo, function(config){ return config.typeId ===  asset.typeId; }).title ;
        });
    };

    this.createCriticalAssetsVerificationInfoForm = function (verificationsInfo) {
        var sortAssets = function (values) {
            var assetOrdering = [
                'Ajoneuvokohtaiset rajoitukset',
                'Esterakennelma',
                'Kääntymisrajoitus',
                'Liikennemerkit',
                'Nopeusrajoitus',
                'Suurin sallittu korkeus',
                'Suurin sallittu massa',
                'VAK-rajoitus'
            ];

            return _.sortBy(values, function(property) {
                return _.indexOf(assetOrdering, property.assetName);
            });
        };

        var verificationTableHeaderRow = function () {
            return '<thead><th id="name">TIETOLAJI</th> <th id="date">TARKISTETTU</th> <th id="verifier">TUNNUS</th></thead>';
        };

        var verificationTableContentRows = function (values) {
            renameAssets(values);
            values = sortAssets(values);
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

    this.createLatestsAssetModificationsInfoForm = function (modificationsInfo) {
        var latestModificationTableHeaderRow = function () {
            return '<thead><th id="verifier">TUNNUS</th> <th id="date">PVM</th> <th id="name">TIETOLAJI</th></thead>';
        };

        var latestModificationTableContentRows = function (values) {
            renameAssets(values);
            return "<tbody>" +
                _.map(values, function (asset) {
                    return '' +
                        "<tr>" +
                        "<td headers='verifier'>" + asset.modified_by + "</td>" +
                        "<td headers='date'>" + asset.modified_date + "</td>" +
                        "<td headers='name'>" + asset.assetName + "</td>" +
                        "</tr>";
                }).join("</tbody>");
        };

        var tableForGroupingLatestModificationValues = function (values) {
            return '' +
                '<table>' +
                latestModificationTableHeaderRow() +
                latestModificationTableContentRows(values) +
                '</table>';
        };

        return '<div id="dashBoardAssetsModificationsInfo">' + tableForGroupingLatestModificationValues(modificationsInfo) + '</div>';
    };

    var renderDialog = function(verificationsInfo, modificationsInfo, totalSuggestedAssets) {
        $('#municipality-situation').append(me.createMunicipalitySituationPopUp(verificationsInfo, modificationsInfo, totalSuggestedAssets)).show();

        $('.confirm-modal#municipalitySituation .cancel').on('click', function() {
            options.cancelCallback();
        });
        $('.confirm-modal#municipalitySituation .save').on('click', function() {
            options.saveCallback();
        });

        $(' .confirm-modal#municipalitySituation .sulje').on('click', function() {
            options.closeCallback();
        });
    };

    this.createMunicipalitySituationPopUp = function (verificationsInfo, modificationsInfo, totalSuggestedAssets) {
        var suggestionToolTipMessage = totalSuggestedAssets + " vihjettä odottaa kuntaylläpitäjän käsittelyä";

        return '' +
            '<div class="modal-overlay confirm-modal" id="municipalitySituation">' +
                '<div class="modal-dialog municipalitySituation">' +
                    '<div class="content">' + options.message + '<a class="header-link sulje">Sulje</a></div>' +
                    '<label class="control-label" id="title">Viimeisimmät päivitykset</label>' +
                    me.createLatestsAssetModificationsInfoForm(modificationsInfo) +
                    '<label class="control-label" id="title">Tarkistetut tietolajit</label>' +
                    me.createCriticalAssetsVerificationInfoForm(verificationsInfo) +
                    '<div class="actions">' +
                        '<button class="btn btn-primary save" onclick="window.location.href=\'#work-list/municipality\'">' +
                            '<span>' + options.saveButton + '</span>' +
                            '<span class="badge" title="' + suggestionToolTipMessage + '">' + totalSuggestedAssets + '</span>' +
                        '</button>' +
                        '<button class="btn btn-secondary cancel">' + options.cancelButton + '</button>' +
                    '</div>' +
                '</div>' +
            '</div>';
    };


};