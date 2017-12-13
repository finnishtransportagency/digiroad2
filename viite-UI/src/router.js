(function (root) {
  root.URLRouter = function(map, backend, models) {
    var Router = Backbone.Router.extend({
      initialize: function () {
        // Support legacy format for opening mass transit stop via ...#300289

        this.route(/^(\d+)$/, function (layer) {
          applicationModel.selectLayer(layer);
        });

        this.route(/^([A-Za-z]+)\/?$/, function (layer) {
          applicationModel.selectLayer(layer);
        });

        this.route(/^$/, function () {
          applicationModel.selectLayer('linkProperty');
        });
      },

      routes: {
        'linkProperty/:linkId': 'linkProperty',
        'linkProperty/mml/:mmlId': 'linkPropertyByMml',
        'roadAddressProject/:projectId': 'roadAddressProject',
        'historyLayer/:date': 'historyLayer',
        'work-list/floatingRoadAddress' : 'floatingAddressesList'
      },

      linkProperty: function (linkId) {
        applicationModel.selectLayer('linkProperty');
        backend.getRoadLinkByLinkId(linkId, function (response) {
          eventbus.once('roadLinks:afterDraw', function () {
            models.selectedLinkProperty.open(response.linkId, response.id, true);
            eventbus.trigger('linkProperties:reselect');
          });
          map.getView().setCenter([response.middlePoint.x, response.middlePoint.y]);
          map.getView().setZoom(12);
        });
      },

      linkPropertyByMml: function (mmlId) {
        applicationModel.selectLayer('linkProperty');
        backend.getRoadLinkByMmlId(mmlId, function (response) {
          eventbus.once('linkProperties:available', function () {
            models.selectedLinkProperty.open(response.id);
          });
          map.getView().setCenter([response.middlePoint.x, response.middlePoint.y]);
          map.getView().setZoom(12);
        });
      },

      roadAddressProject: function (projectId) {
        applicationModel.selectLayer('roadAddressProject');
        var parsedProjectId = parseInt(projectId);
        eventbus.trigger('roadAddressProject:startProject', parsedProjectId, true);
      },

      historyLayer: function (date) {
        applicationModel.selectLayer('linkProperty');
        var dateSeparated = date.split('-');
        eventbus.trigger('suravageProjectRoads:toggleVisibility', false);
        eventbus.trigger('suravageRoads:toggleVisibility', false);
        $('.suravage-visible-wrapper').hide();
        $('#toggleEditMode').hide();
        $('#emptyFormDiv,#projectListButton').hide();
        eventbus.trigger('linkProperty:fetchHistoryLinks', dateSeparated);
      },

      floatingAddressesList: function () {
        eventbus.trigger('workList:select', 'linkProperty', backend.getFloatingRoadAddresses());
      }
    });



    var router = new Router();

    // We need to restart the router history so that tests can reset
    // the application before each test.
    Backbone.history.stop();
    Backbone.history.start();

    eventbus.on('linkProperties:unselected', function () {
      router.navigate('linkProperty');
    });

    eventbus.on('roadAddressProject:selected', function (id, layerName, selectedLayer) {
      router.navigate('roadAddressProject/' + id);
    });

    eventbus.on('linkProperties:selected', function (linkProperty) {
      if(!_.isEmpty(models.selectedLinkProperty.get())){
        if(_.isArray(linkProperty)){
          router.navigate('linkProperty/' + _.first(linkProperty).linkId);
        } else {
          router.navigate('linkProperty/' + linkProperty.linkId);
        }
      }
    });

    eventbus.on('linkProperties:selectedProject', function (linkId, project) {
      if(typeof project.id !== 'undefined') {
        var baseUrl = 'roadAddressProject/' + project.id;
        var linkIdUrl = typeof linkId !== 'undefined' ? '/' + linkId : '';
        router.navigate(baseUrl + linkIdUrl);
        if(project.coordX !== 0 && project.coordY !== 0 && project.zoomLevel !== 0){
          applicationModel.selectLayer('linkProperty', false);
          map.getView().setCenter([project.coordX, project.coordY]);
          map.getView().setZoom(project.zoomLevel);
        }
        else if (typeof linkId !== 'undefined') {
          applicationModel.selectLayer('linkProperty', false);
          backend.getRoadLinkByLinkId(linkId, function (response) {
            map.getView().setCenter([response.middlePoint.x, response.middlePoint.y]);
          });
        }
      }
    });

    eventbus.on('layer:selected', function (layer) {
      if(layer.indexOf('/') === -1){
        layer = layer.concat('/');
      }
      router.navigate(layer);
    });
  };
})(this);
