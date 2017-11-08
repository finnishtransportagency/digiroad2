(function(application) {
  application.start = function(customBackend, withTileMaps) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ? true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var projectCollection = new ProjectCollection(backend);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var selectedProjectLinkProperty = new SelectedProjectLink(projectCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var instructionsPopup = new InstructionsPopup(jQuery('.digiroad2'));
    var projectChangeInfoModel = new ProjectChangeInfoModel(backend);

    var models = {
      roadCollection: roadCollection,
      projectCollection: projectCollection,
      selectedLinkProperty: selectedLinkProperty,
      linkPropertiesModel: linkPropertiesModel,
      selectedProjectLinkProperty : selectedProjectLinkProperty
    };

    bindEvents();
    window.applicationModel = new ApplicationModel([
      selectedLinkProperty]);

    var linkGroups = groupLinks(selectedProjectLinkProperty);

    var projectListModel = new ProjectListModel(projectCollection);
    var projectChangeTable = new ProjectChangeTable(projectChangeInfoModel, models.projectCollection);

    NavigationPanel.initialize(
      jQuery('#map-tools'),
      new SearchBox(
        instructionsPopup,
        new LocationSearch(backend, window.applicationModel)
      ),
      new ProjectSelectBox(projectListModel),
        linkGroups
    );

    backend.getUserRoles();
    backend.getStartupParametersWithCallback(function (startupParameters) {
      startApplication(backend, models, tileMaps, startupParameters, projectChangeTable);
    });
  };

  var startApplication = function(backend, models, withTileMaps, startupParameters, projectChangeTable) {
    setupProjections();
    fetch('components/WMTSCapabilities.xml', {credentials: "include"}).then(function(response) {
      return response.text();
    }).then(function(arcConfig) {
      var map = setupMap(backend, models, withTileMaps, startupParameters, arcConfig, projectChangeTable);
      new URLRouter(map, backend, models);
      eventbus.trigger('application:initialized');
    });
  };

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var createOpenLayersMap = function(startupParameters, layers) {
    var map = new ol.Map({
      keyboardEventTarget: document,
      target: 'mapdiv',
      layers: layers,
      view: new ol.View({
        center: [startupParameters.lon, startupParameters.lat],
        projection: 'EPSG:3067',
        zoom: startupParameters.zoom,
        resolutions: [2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625]
      })
    });

    var ctrlDragZoom = new ol.interaction.DragZoom({
      duration: 1500,
      condition: function(mapBrowserEvent) {
        var originalEvent = mapBrowserEvent.originalEvent;
        return (
        originalEvent.ctrlKey &&
        !(originalEvent.metaKey || originalEvent.altKey) &&
        !originalEvent.shiftKey);
      }
    });
    map.getInteractions().forEach(function(interaction) {
      if (interaction instanceof ol.interaction.DragZoom) {
        map.removeInteraction(interaction);
      }
    }, this);

    ctrlDragZoom.setActive(true);
    map.addInteraction(ctrlDragZoom);
    map.setProperties({extent : [-548576, 6291456, 1548576, 8388608]});
    return map;
  };

  var setupMap = function(backend, models, withTileMaps, startupParameters, arcConfig, projectChangeTable) {
    var tileMaps = new TileMapCollection(map, arcConfig);

    var map = createOpenLayersMap(startupParameters, tileMaps.layers);

    var mapOverlay = new MapOverlay(jQuery('.container'));
    var styler = new Styler();
    var roadLayer = new RoadLayer3(map, models.roadCollection, styler, models.selectedLinkProperty);
    var projectLinkLayer = new ProjectLinkLayer(map, models.projectCollection, models.selectedProjectLinkProperty, roadLayer);

    new LinkPropertyForm(models.selectedLinkProperty);

    new ProjectForm(models.projectCollection, models.selectedProjectLinkProperty);
    new ProjectEditForm(models.projectCollection, models.selectedProjectLinkProperty, projectLinkLayer, projectChangeTable, backend);
    new SplitForm(models.projectCollection, models.selectedProjectLinkProperty, projectLinkLayer, projectChangeTable, backend);

    var layers = _.merge({
      road: roadLayer,
      roadAddressProject: projectLinkLayer,
      linkProperty: new LinkPropertyLayer(map, roadLayer, models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel, styler)});

    var mapPluginsContainer = jQuery('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new CoordinatesDisplay(map, mapPluginsContainer);

    // Show environment name next to Digiroad logo
    jQuery('#notification').append(Environment.localizedName());
    jQuery('#notification').append(' Päivämäärä: ' + startupParameters.deploy_date);

    // Show information modal in integration environment (remove when not needed any more)
    if (Environment.name() === 'integration') {
      showInformationModal('Huom!<br>Olet integraatiotestiympäristössä.');
    }

    new MapView(map, layers, new InstructionsPopup(jQuery('.digiroad2')));

    applicationModel.moveMap(map.getView().getZoom(), map.getLayers().getArray()[0].getExtent());

    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  function groupLinks(selectedProjectLinkProperty) {

    var roadLinkBox = new RoadLinkBox(selectedProjectLinkProperty);

    return [
      [roadLinkBox]
    ];
  }

  // Shows modal with message and close button
  function showInformationModal(message) {
    jQuery('.container').append('<div class="modal-overlay confirm-modal" style="z-index: 2000"><div class="modal-dialog"><div class="content">' + message + '</div><div class="actions"><button class="btn btn-secondary close">Sulje</button></div></div></div></div>');
    jQuery('.confirm-modal .close').on('click', function() {
      jQuery('.confirm-modal').remove();
    });
  }

  application.restart = function(backend, withTileMaps) {
    this.start(backend, withTileMaps);
  };

  var bindEvents = function() {

    eventbus.on('linkProperties:saving', function() {
      indicatorOverlay();
    });

    eventbus.on('linkProperties:available', function() {
      jQuery('.spinner-overlay').remove();
    });

    eventbus.on('confirm:show', function() { new Confirm(); });
  };

}(window.Application = window.Application || {}));
