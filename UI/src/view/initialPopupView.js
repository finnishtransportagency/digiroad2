window.InitialPopupView = function(backend, location, userRole, places, assetTypeConfig, defaultAsset, municipality) {

  var options = {
    confirmButton: 'Tallenna',
    cancelButton: 'Peruuta',
    successCallback: function () {
      var selectedLocation = $('#location').val();
      var selectedAssetType = parseInt($('#assetType').val(), 10);
      var selectedLocationName = _.isNull(selectedLocation) ? undefined : $('#location option:selected').text();
      var selectedAssetTypeName = _.isNaN(selectedAssetType) ? undefined : $('#assetType option:selected').text();
      var defaultParameters = {lon: null, lat: null, zoom: null, assetType: selectedAssetType, municipalityId: null, elyId: null};

      if (selectedLocation === "currentLocation") {
        defaultParameters.lon = location.lon;
        defaultParameters.lat = location.lat;
        defaultParameters.zoom = location.zoom;
      }
      else if (selectedLocation === "suomi") {
        defaultParameters.lon = 390000;
        defaultParameters.lat = 6900000;
        defaultParameters.zoom = 2;
      }
      else {
        if (userRole !== "elyMaintainer") {
          defaultParameters.municipalityId = parseInt(selectedLocation, 10);
        }
        else {
          defaultParameters.elyId = parseInt(selectedLocation,10);
        }
      }

      backend.updateUserConfigurationDefaultLocation(defaultParameters,
        function () {
          eventbus.trigger('userInfo:setDefaultLocation', selectedAssetTypeName, selectedLocationName);
        });

      setTimeout(function () {new GenericConfirmPopup("Aloitusnäkymä päivitetty.", {type: "alert"});}, 1);
    },
    closeCallback: function () {},
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

        var options = '' +
            '<option value=' + 0 + '>' +
            "Tielinkki" +
            '</option>';
        _.map(assetTypes, function (typeId) {
            var type = assetTypeConfig.assetTypeInfo.find(function (asset) {
                return asset.typeId === typeId;
            });

            options += assetsOptions(type);
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

        var options = '';
        _.map(assetTypes, function (typeId) {
            var type = assetTypeConfig.assetTypeInfo.find(function (asset) {
                return asset.typeId === typeId;
            });

            options += assetsOptions(type);
        });
        return options;
    };

    var confirmDiv =
        '<div class="modal-overlay confirm-modal">' +
            '<div class="modal-dialog">' +
                '<div class="content">' +
                    '<b>Aloitussivu</b>' +
                    '<a class="header-link cancel" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
                '</div>' +

                '<div class="contentNoBackColor">' +
                    '<div>' +
                        '<p>Aloitussijainti<span class="municipalityElyText">' + municipality + '</span></p>' +
                    '</div>' +

                    '<div>' +
                    '<select id="location" class="form-dark form-control municipalityEly" name="places">' +
                        '<option value="" disabled selected>Valitse sijainti</option>' +
                        '<option value="currentLocation">Valitse nykyinen karttasijainti</option>' +
                        '<option value="suomi">Suomi</option>' +
                        municipalityDropdown() +
                    '</select>' +
                    '</div>' +
                '</div>' +


                '<div class="contentNoBackColor">' +
                    '<div>' +
                        '<p>Tielolaji<span class="assetTypeText">' + defaultAsset + '</span></p>'+
                    '</div>' +
                    '<div>' +
                        '<select id="assetType" class="form-dark form-control assetType"  name="form-control places">' +
                            '<option value="" disabled selected>Valitse omaisuuslaji</option>' +
                            '<option value="linearAssets" disabled>Viivamaiset kohteet</option>' +
                                linearAssetsDropdown() +
                            '<option value="pointAssets" disabled>Pistemäiset kohteet</option>' +
                            pointAssetsDropdown() +
                        '</select>' +
                    '</div>' +
                '</div>' +

                '<br>' +
                '<br>' +
                '<br>' +

                '<div class="actions" style ="float: right">' +
                    '<button class = "btn btn-secondary cancel">' + options.cancelButton + '</button>' +
                    '<button class = "btn btn-primary confirm" disabled>' + options.confirmButton + '</button>' +
                '</div>' +
            '</div>' +
        '</div>';

  var renderConfirmDialog = function () {
    var template = confirmDiv;

    $(options.container).append(template);
    var modal = $('.modal-dialog');
  };

  var setConfirmButtonState = function () {
    $('.confirm-modal .confirm').prop('disabled', _.isEmpty($('.confirm-modal #location').val()) && _.isEmpty($('.confirm-modal #assetType').val()));
  };

  var bindEvents = function () {
    $('.confirm-modal .cancel').on('click', function () {
      purge();
      options.closeCallback();
    });
    $('.confirm-modal .confirm').on('click', function () {
      options.successCallback();
      purge();
    });
    $('#location,#assetType').change(function () {
      setConfirmButtonState();
    });
  };

  var show = function () {
    purge();
    renderConfirmDialog();
    bindEvents();
  };

  var purge = function () {
    $('.confirm-modal').remove();
  };

  show();
};