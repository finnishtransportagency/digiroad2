(function (root) {
    root.ManoeuvreSamuutusWorkList = function () {

        WorkListView.call(this);
        var me = this;
        var backend;
        var enumerations = new Enumerations();
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
            var tableContentRows = function (items) {
                return items.map(function (item) {
                    return $('<tr/>')
                        .append(checkbox(item.assetId))
                        .append($('<td/>').html(changeRow(item)))
                        .append($('<td/>').append(openMapButton(item)));
                });
            };

            var changeRow = function (item) {
                var exceptionTypesEnumerations = item.exceptionTypes.map(function (typeId) {
                    var exception = enumerations.manoeuvreExceptions.find(function (exception) {
                        return exception.typeId === typeId;
                    });
                    return exception ? exception.title : "Unknown Type";
                });

                var idRow = "<p><strong>Kääntymisrajoituksen ID:</strong> " + item.assetId + "</p>";
                var linksRow = "<p><strong>Tielinkit:</strong> " + item.links + "</p>";
                var validityPeriodsRow = "<p><strong>Voimassaoloajat:</strong></p><ul>";

                item.validityPeriods.forEach(function (period) {
                    var dayEnumeration = enumerations.manoeuvreValidityPeriodDays.find(function (day) {
                        return day.value === period.days.value;
                    });
                    validityPeriodsRow += "<li>Viikonpäivät: " + dayEnumeration.title + ", Kellonaika: " +
                        period.startHour + ":" + period.startMinute + " - " +
                        period.endHour + ":" + period.endMinute + "</li>";
                });

                validityPeriodsRow += "</ul>";
                var exceptionTypesRow = "<p><strong>Rajoitus ei koske seuraavia tyyppejä:</strong> " + exceptionTypesEnumerations.join(", ") + "</p>";
                var additionalInfoRow = "<p><strong>Muu tarkenne:</strong> " + item.additionalInfo + "</p>";
                var createdDateRow = "<p><strong>Työlistakohteen luontipäivänmäärä:</strong> " + item.createdDate + "</p>";

                return $('<div/>').html(idRow + linksRow + validityPeriodsRow + exceptionTypesRow + additionalInfoRow + createdDateRow);
            };

            var checkbox = function (itemId) {
                return $('<td class="manoeuvreWorkListCheckboxWidth"/>').append($('<input type="checkbox" class="verificationCheckbox"/>').val(itemId));
            };

            var openMapButton = function (item) {
                return $('<button/>')
                    .addClass('delete btn btn-municipality')
                    .text('Avaa kartalla')
                    .click(function () {
                        openMapPopup(item);
                    });
            };

            var deleteBtn = function () {
                return $('<button disabled/>').attr('id', 'deleteWorkListItems').addClass('delete btn btn-municipality').text('Poista valitut kohteet').click(function () {
                    new GenericConfirmPopup("Haluatko varmasti poistaa valitut kääntymisrajoitukset työlistasta?", {
                        container: '#work-list',
                        successCallback: function () {
                            $(".verificationCheckbox:checkbox:checked").each(function () {
                                selectedToDelete.push(parseInt(($(this).attr('value'))));
                            });
                            backend.deleteManoeuvresWorkListItems(selectedToDelete, function () {
                                new GenericConfirmPopup("Valitut kääntymisrajoitukset poistettu työlistalta!", {
                                    container: '#work-list',
                                    type: "alert",
                                    okCallback: function () {
                                        location.reload();
                                    }
                                });
                            }, function () {
                                new GenericConfirmPopup("Valittuja kääntymisrajoituksia ei voitu poistaa työlistalta. Yritä myöhemmin uudelleen!", {
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

            var addTable = function (manoeuvreWorkListItems) {
                if (!manoeuvreWorkListItems || manoeuvreWorkListItems.length === 0) return '';
                return $('<table><tbody>').addClass('table')
                    .append(tableContentRows(manoeuvreWorkListItems))
                    .append('</tbody></table>');
            };

            return $('<div/>')
                .append(deleteBtn())
                .append(addTable(workListItems));
        };

        function openMapPopup(item) {
            var modal = $('<div id="mapModal" class="modal">')
                .css({
                    position: 'fixed',
                    top: '0',
                    left: '0',
                    width: '100%',
                    height: '100%',
                    backgroundColor: 'rgba(0,0,0,0.5)',
                    zIndex: '1000',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center'
                });

            var modalContent = $('<div class="modal-content">')
                .css({
                    width: '80%',
                    height: '80%',
                    backgroundColor: '#fff',
                    padding: '20px',
                    position: 'relative'
                });

            var closeButton = $('<button>Sulje kartta</button>').css({
                position: 'absolute',
                top: '10px',
                right: '10px',
                zIndex: '1001'
            }).click(function() {
                modal.remove();
            });

            var mapDiv = $('<div id="pop-up-mapdiv">').css({
                width: '100%',
                height: '100%'
            });

            var pluginsDiv = $('<div>', { id: 'pop-up-map-plugins' });

            //mapDiv.append(pluginsDiv);
            modalContent.append(closeButton).append(mapDiv);
            modal.append(modalContent);
            $('body').append(modal);

            //TODO jätä pos globaalien application.js muuttujien käyttö, luo oma uusi pop-up kartta .js
            var models = window.models;
            var linearAssets = window.linearAssets;
            var pointAssets = window.pointAssets;
            var withTileMaps = true;
            var startupParameters = {lon:390000, lat: 6900000, zoom: 2};
            var roadCollection = window.roadCollection;
            var verificationInfoCollection = window.verificationCollection;
            var assetConfiguration = window.assetConfiguration;
            var isExperimental = false;
            var clusterDistance = 60;


            var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance);

            map.setTarget('mapdiv');
        }


// ------------------
        var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters, roadCollection, verificationInfoCollection, assetConfiguration, isExperimental, clusterDistance) {
            var tileMaps = new TileMapCollection(map, "");

            var map = createOpenLayersMap(startupParameters, tileMaps.layers);

            var mapOverlay = new MapOverlay($('.container'));

            var mapPluginsContainer = $('#pop-up-map-plugins');
            new ScaleBar(map, mapPluginsContainer);
            new TileMapSelector(mapPluginsContainer);
            new ZoomBox(map, mapPluginsContainer);
            new MunicipalityDisplay(map, mapPluginsContainer, backend);
            new LoadingBarDisplay(map, mapPluginsContainer);

            if (withTileMaps) { new TileMapCollection(map); }
            var roadLayer = new RoadLayer(map, models.roadCollection);

            new LinkPropertyForm(models.selectedLinkProperty, new FeedbackModel(backend, assetConfiguration, models.selectedLinkProperty));
            new ManoeuvreForm(models.selectedManoeuvreSource, new FeedbackModel(backend, assetConfiguration, models.selectedManoeuvreSource));
            _.forEach(linearAssets, function(linearAsset) {
                if(linearAsset.form)
                    linearAsset.form.initialize(linearAsset, new FeedbackModel(backend, assetConfiguration, linearAsset.selectedLinearAsset));
                else
                    LinearAssetForm.initialize(
                        linearAsset,
                        AssetFormElementsFactory.construct(linearAsset),
                        new FeedbackModel(backend, assetConfiguration, linearAsset.selectedLinearAsset)
                    );
            });

            _.forEach(pointAssets, function(pointAsset ) {
                var parameters = {
                    pointAsset: pointAsset,
                    roadCollection: pointAsset.roadCollection,
                    applicationModel: applicationModel,
                    backend: backend,
                    saveCondition: pointAsset.saveCondition || function() {return true;},
                    feedbackCollection : new FeedbackModel(backend, assetConfiguration, pointAsset.selectedPointAsset),
                    collection: pointAsset.collection
                };

                if(pointAsset.form) {
                    new pointAsset.form().initialize(parameters);
                }else
                    new PointAssetForm().initialize(parameters);
            });


            var linearAssetLayers = _.reduce(linearAssets, function(acc, asset) {
                var parameters = {
                    map: map,
                    application: applicationModel,
                    collection: asset.collection,
                    selectedLinearAsset: asset.selectedLinearAsset,
                    roadCollection: models.roadCollection,
                    roadLayer: roadLayer,
                    layerName: asset.layerName,
                    multiElementEventCategory: asset.multiElementEventCategory,
                    singleElementEventCategory: asset.singleElementEventCategory,
                    style: asset.style || new PiecewiseLinearAssetStyle(),
                    formElements: asset.form ?  asset.form : AssetFormElementsFactory.construct(asset),
                    assetLabel: asset.label,
                    roadAddressInfoPopup: roadAddressInfoPopup,
                    authorizationPolicy: asset.authorizationPolicy,
                    readOnlyLayer: asset.readOnlyLayer ? new asset.readOnlyLayer({ layerName: asset.layerName, map: map, backend: backend }): false,
                    laneReadOnlyLayer: asset.laneReadOnlyLayer,
                    massLimitation: asset.editControlLabels.additionalInfo,
                    typeId: asset.typeId,
                    isMultipleLinkSelectionAllowed: asset.isMultipleLinkSelectionAllowed,
                    minZoomForContent: asset.minZoomForContent,
                    isExperimental: isExperimental
                };
                acc[asset.layerName] = asset.layer ? new asset.layer(parameters) : new LinearAssetLayer(parameters);
                return acc;

            }, {});

            var pointAssetLayers = _.reduce(pointAssets, function(acc, asset) {
                var parameters = {
                    roadLayer: roadLayer,
                    application: applicationModel,
                    roadCollection: asset.roadCollection,
                    collection: asset.collection,
                    map: map,
                    selectedAsset: asset.selectedPointAsset,
                    style: PointAssetStyle(asset.layerName),
                    mapOverlay: mapOverlay,
                    layerName: asset.layerName,
                    assetLabel: asset.label,
                    newAsset: asset.newAsset,
                    roadAddressInfoPopup: roadAddressInfoPopup,
                    allowGrouping: asset.allowGrouping,
                    assetGrouping: new AssetGrouping(asset.groupingDistance),
                    authorizationPolicy: asset.authorizationPolicy,
                    showRoadLinkInfo: asset.showRoadLinkInfo,
                    readOnlyLayer: asset.readOnlyLayer ? new asset.readOnlyLayer({ layerName: asset.layerName, map: map, backend: backend }): false
                };

                acc[asset.layerName] = asset.layer ? new asset.layer(parameters) : new PointAssetLayer(parameters);
                return acc;

            }, {});

            var layers = _.merge({
                road: roadLayer,
                linkProperty: new LinkPropertyLayer(map, roadLayer, models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel, roadAddressInfoPopup, isExperimental),
                massTransitStop: new MassTransitStopLayer(map, models.roadCollection, mapOverlay, new AssetGrouping(36), roadLayer, roadAddressInfoPopup, isExperimental, clusterDistance),
                speedLimit: new SpeedLimitLayer({
                    map: map,
                    application: applicationModel,
                    collection: models.speedLimitsCollection,
                    selectedSpeedLimit: models.selectedSpeedLimit,
                    readOnlyLayer: new TrafficSignReadOnlyLayer({ layerName: 'speedLimit', map: map, backend: backend }),
                    style: SpeedLimitStyle(applicationModel),
                    roadLayer: roadLayer,
                    roadAddressInfoPopup: roadAddressInfoPopup,
                    isExperimental: isExperimental
                }),
                manoeuvre: new ManoeuvreLayer(applicationModel, map, roadLayer, models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection,  new TrafficSignReadOnlyLayer({ layerName: 'manoeuvre', map: map, backend: backend }),  new LinearSuggestionLabel() )

            }, linearAssetLayers, pointAssetLayers);

            new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

            applicationModel.moveMap(zoomlevels.getViewZoom(map), map.getLayers().getArray()[0].getExtent());
            backend.getUserRoles();
            return map;
        };

        var createOpenLayersMap = function(startupParameters, layers) {
            var map = new ol.Map({
                target: 'pop-up-mapdiv',
                layers: layers,
                view: new ol.View({
                    center: [startupParameters.lon, startupParameters.lat],
                    projection: 'EPSG:3067',
                    zoom: startupParameters.zoom,
                    resolutions: [2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625]
                }),
                interactions: ol.interaction.defaults({
                    mouseWheelZoom: false
                }),
            });
            map.setProperties({extent : [-548576, 6291456, 1548576, 8388608]});
            map.addInteraction(new ol.interaction.DragPan({
                condition: function (mapBrowserEvent) {
                    var originalEvent = mapBrowserEvent.originalEvent;
                    return (!originalEvent.altKey && !originalEvent.shiftKey);
                }
            }));

            map.addInteraction(new ol.interaction.MouseWheelZoom({
                condition: function(event) {
                    var deltaY = event.originalEvent.deltaY;
                    if (deltaY > 0) {
                        // Wheel scrolled down (Zoom out)
                        return applicationModel.handleZoomOut(map);
                    } else if (deltaY < 0) {
                        // Wheel scrolled up (Zoom in)
                        return applicationModel.handleZoomIn(map);
                    } else {
                        // no zoom -> no reason to block
                        return true;
                    }
                }
            }));
            return map;
        };


    };
})(this);
