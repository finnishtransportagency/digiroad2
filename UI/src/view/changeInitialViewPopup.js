window.ChangeInitialViewPopup = function(backend, location, userRole, places, assetTypeConfig, defaultAsset, municipality) {

    var options = {
        confirmButton: 'Tallenna',
        cancelButton: 'Peruuta',
        successCallback: function(){
            var selectedLocation = $('#location').val();
            var selectedAssetType = $('#assetType').val();

            if (selectedLocation === null && selectedAssetType === null){
                setTimeout(function() { new GenericConfirmPopup("Mikään ei valittu.", {type: 'alert'});}, 1);
            }
            else if(selectedLocation == "currentLocation"){
                backend.updateUserConfigurationDefaultLocation(location);
                backend.updateUserConfig("null", "null", selectedAssetType);
            }
            else if(selectedLocation == "suomi"){
                var suomi = {lon: 390000, lat: 6900000, zoom: 2};
                backend.updateUserConfigurationDefaultLocation(suomi);
                backend.updateUserConfig("null", "null", selectedAssetType);
            }
            else{
                if(userRole != "busStopMaintainer"){
                    backend.updateUserConfig("null", selectedLocation, selectedAssetType);
                }
                else
                {
                    backend.updateUserConfig(selectedLocation, "null", selectedAssetType);
                }
            }
            setTimeout(function() { new GenericConfirmPopup("Laskeutumissivu päivitetty.", {type: 'alert'});}, 1);
        },
        closeCallback: function(){},
        container: '.container'
    };

    var placesOptions = function (municipality) {
        return '' +
            '<option value=' + municipality.id + '>' +
            municipality.name +
            '</option>';
    };

    var municipalityDropdown = function () {
        return _.map(places, function(municipality) {
            return placesOptions(municipality).concat('');
        });
    };

    var assetsOptions = function (assetType) {
        return '' +
            '<option value=' + assetType.typeId + '>' +
            assetType.title +
            '</option>';
    };

    var linearAssetsDropdown = function () {
        var assetTypes = [
            assetTypeConfig.assetTypes.speedLimit,
            assetTypeConfig.assetTypes.manoeuvre,
            assetTypeConfig.assetTypes.prohibition,
            assetTypeConfig.assetTypes.parkingProhibition,
            assetTypeConfig.assetTypes.hazardousMaterialTransportProhibition,

            assetTypeConfig.assetTypes.totalWeightLimit,
            assetTypeConfig.assetTypes.trailerTruckWeightLimit,
            assetTypeConfig.assetTypes.axleWeightLimit,
            assetTypeConfig.assetTypes.bogieWeightLimit,
            assetTypeConfig.assetTypes.heightLimit,
            assetTypeConfig.assetTypes.lengthLimit,
            assetTypeConfig.assetTypes.widthLimit,

            assetTypeConfig.assetTypes.pavedRoad,
            assetTypeConfig.assetTypes.roadWidth,
            assetTypeConfig.assetTypes.litRoad,
            assetTypeConfig.assetTypes.carryingCapacity,
            assetTypeConfig.assetTypes.roadDamagedByThaw,
            assetTypeConfig.assetTypes.roadWorksAsset,

            assetTypeConfig.assetTypes.europeanRoads,
            assetTypeConfig.assetTypes.exitNumbers,
            assetTypeConfig.assetTypes.careClass,
            assetTypeConfig.assetTypes.numberOfLanes,
            assetTypeConfig.assetTypes.massTransitLane,
            assetTypeConfig.assetTypes.winterSpeedLimit,
            assetTypeConfig.assetTypes.trafficVolume
        ];

        var i = 0;
        var options = '' +
            '<option value=' + 0 + '>' +
            "Tielinkki" +
            '</option>';
        _.map(assetTypes, function (typeId) {
            var type = assetTypeConfig.assetTypeInfo.find(function (asset) {
                return asset.typeId === typeId;
            });

            options += assetsOptions(type);
            i++;
        });
    return options;
    };

    var pointAssetsDropdown = function () {
        var assetTypes = [
            assetTypeConfig.assetTypes.massTransitStop,
            assetTypeConfig.assetTypes.obstacles,
            assetTypeConfig.assetTypes.railwayCrossings,
            assetTypeConfig.assetTypes.directionalTrafficSigns,
            assetTypeConfig.assetTypes.pedestrianCrossings,
            assetTypeConfig.assetTypes.trafficLights,
            assetTypeConfig.assetTypes.trafficSigns,
            assetTypeConfig.assetTypes.servicePoints,

            assetTypeConfig.assetTypes.trHeightLimits,
            assetTypeConfig.assetTypes.trWidthLimits,
            assetTypeConfig.assetTypes.trWeightLimits
        ];

        var i = 0;
        var options = '';
        _.map(assetTypes, function (typeId) {
            var type = assetTypeConfig.assetTypeInfo.find(function (asset) {
                return asset.typeId === typeId;
            });

            options += assetsOptions(type);
            i++;
        });
        return options;
    };

    var confirmDiv =
        '<div class="modal-overlay confirm-modal">' +
            '<div class="modal-dialog">' +
                '<div class="content">' +
                    '<b>' + "Aloitussivu" + '</b>' +
                    '<a class="header-link no" href="#">' + "Sulje" + '</a>' +
                '</div>' +

                '<div class="contentNoBackColor">' +
                    '<div>' +
                        '<p>' + "Aloitussijainti" + '<span class="municipalityElyText">' + municipality + '</span>' + '</p>' +
                    '</div>' +

                    '<div>' +
                    '<select id="location" class="form-dark form-control municipalityEly" name="places">' +
                        '<option disabled selected>' + "Valitse sijainti" + '</option>' +
                        '<option value="currentLocation">' + "Valitse nykyinen karttasijainti" +'</option>' +
                        '<option value="suomi">'+ "Suomi" +'</option>' +
                        municipalityDropdown() +
                    '</select>' +
                    '</div>' +
                '</div>' +


                '<div class="contentNoBackColor">' +
                    '<div>' +
                        '<p>' + "Tielolaji" + '<span class="assetTypeText">' + defaultAsset + '</span>' + '</p>'+
                    '</div>' +
                    '<div>' +
                        '<select id="assetType" class="form-dark form-control assetType"  name="form-control places">' +
                            '<option disabled selected>' + "Valitse omaisuuslaji" + '</option>' +
                            '<option value="linearAssets" disabled>' + "Viivamaiset kohteet" +'</option>' +
                                linearAssetsDropdown() +
                            '<option value="pointAssets" disabled>' + "Pistemäiset kohteet" +'</option>' +
                            pointAssetsDropdown() +
                        '</select>' +
                    '</div>' +
                '</div>' +

                '<br>' +
                '<br>' +
                '<br>' +

                '<div class="actions" style ="float: right">' +
                    '<button class = "btn btn-secondary no">' + options.cancelButton + '</button>' +
                    '<button class = "btn btn-primary yes">' + options.confirmButton + '</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    var renderConfirmDialog = function() {
        var template = confirmDiv;

        jQuery(options.container).append(template);
        var modal = $('.modal-dialog');
    };

    var bindEvents = function() {
        jQuery('.confirm-modal .no').on('click', function() {
            purge();
            options.closeCallback();
        });
        jQuery('.confirm-modal .yes').on('click', function() {
            options.successCallback();
            purge();
        });
    };

    var show = function() {
        purge();
        renderConfirmDialog();
        bindEvents();
    };

    var purge = function() {
        jQuery('.confirm-modal').remove();
    };

    show();
};