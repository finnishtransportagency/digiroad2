(function(root) {
  root.LinkPropertyLayer = function(map, roadLayer, geometryUtils, selectedLinkProperty, roadCollection, linkPropertiesModel, applicationModel) {
    var layerName = 'linkProperty';
    Layer.call(this, layerName, roadLayer);
    var me = this;
    var currentRenderIntent = 'default';
    var linkPropertyLayerStyles = LinkPropertyLayerStyles(roadLayer);
    this.minZoomForContent = zoomlevels.minZoomForRoadLinks;

    roadLayer.setLayerSpecificStyleMapProvider(layerName, function() {
      return linkPropertyLayerStyles.getDatasetSpecificStyleMap(linkPropertiesModel.getDataset(), currentRenderIntent);
    });

    var selectRoadLink = function(feature) {
      selectedLinkProperty.open(feature.attributes.mmlId, feature.singleLinkSelect);
      currentRenderIntent = 'select';
      roadLayer.redraw();
      highlightFeatures();
    };

    var unselectRoadLink = function() {
      currentRenderIntent = 'default';
      selectedLinkProperty.close();
      roadLayer.redraw();
      highlightFeatures();
    };

    var selectControl = new OpenLayers.Control.SelectFeature(roadLayer.layer, {
      onSelect: selectRoadLink,
      onUnselect: unselectRoadLink
    });
    map.addControl(selectControl);
    var doubleClickSelectControl = new DoubleClickSelectControl(selectControl, map);
    this.selectControl = selectControl;

    var showMassUpdateDialog = function(links) {
      selectedLinkProperty.openMultiple(links);

      var functionalClasses = [
        {text: '1', value: 1},
        {text: '2', value: 2},
        {text: '3', value: 3},
        {text: '4', value: 4},
        {text: '5', value: 5},
        {text: '6', value: 6},
        {text: '7', value: 7},
        {text: '8', value: 8}
      ];

      var localizedTrafficDirections = [
        {value: 'BothDirections', text: 'Molempiin suuntiin'},
        {value: 'AgainstDigitizing', text: 'Digitointisuuntaa vastaan'},
        {value: 'TowardsDigitizing', text: 'Digitointisuuntaan'}
      ];

      var linkTypes = [
        {value: 1, text: 'Moottoritie'},
        {value: 2, text: 'Moniajoratainen tie'},
        {value: 3, text: 'Yksiajoratainen tie'},
        {value: 4, text: 'Moottoriliikennetie'},
        {value: 5, text: 'Kiertoliittymä'},
        {value: 6, text: 'Ramppi'},
        {value: 7, text: 'Levähdysalue'},
        {value: 8, text: 'Kevyen liikenteen väylä'},
        {value: 9, text: 'Jalankulkualue'},
        {value: 10, text: 'Huolto- tai pelastustie'},
        {value: 11, text: 'Liitännäisliikennealue'},
        {value: 12, text: 'Ajopolku'},
        {value: 13, text: 'Huoltoaukko moottoritiellä'},
        {value: 21, text: 'Lautta/lossi'}
      ];

      var createOptionElements = function(options) {
        return ['<option value=""></option>'].concat(_.map(options, function(option) {
          return '<option value="' + option.value + '">' + option.text + '</option>';
        })).join('');
      };

      var confirmDiv =
          _.template(
            '<div class="modal-overlay mass-update-modal">' +
            '<div class="modal-dialog">' +
            '<div class="content">' +
            'Olet valinnut <%- linkCount %> tielinkkiä' +
            '</div>' +

            '<div class="form-group editable">' +
              '<label class="control-label">Toiminnallinen luokka</label>' +
              '<select id="functional-class-selection" class="form-control">' + createOptionElements(functionalClasses) + '</select>' +
            '</div>' +

            '<div class="form-group editable">' +
            '<label class="control-label">Liikennevirran suunta</label>' +
            '<select id="traffic-direction-selection" class="form-control">' + createOptionElements(localizedTrafficDirections) + '</select>' +
            '</div>' +

            '<div class="form-group editable">' +
            '<label class="control-label">Tielinkin tyyppi</label>' +
            '<select id="link-type-selection" class="form-control">' + createOptionElements(linkTypes) + '</select>' +
            '</div>' +

            '<div class="actions">' +
            '<button class="btn btn-primary save">Tallenna</button>' +
            '<button class="btn btn-secondary close">Peruuta</button>' +
            '</div>' +
            '</div>' +
          '</div>');

      var renderDialog = function() {
        $('.container').append(confirmDiv({
          linkCount: links.length
        }));
        var modal = $('.modal-dialog');
      };

      var bindEvents = function() {
        me.eventListener.listenTo(eventbus, 'linkProperties:updateFailed', function() {
          selectedLinkProperty.cancel();
          unselectRoadLink();
          me.refreshView();
          me.eventListener.stopListening(eventbus, 'linkProperties:updateFailed');
        });
        $('.mass-update-modal .close').on('click', function() {
          unselectRoadLink();
          purge();
        });
        $('.mass-update-modal .save').on('click', function() {
          var modal = $('.modal-dialog');
          modal.find('.actions button').attr('disabled', true);

          var functionalClassSelection = $('#functional-class-selection').val();
          if (!_.isEmpty(functionalClassSelection)) {
            selectedLinkProperty.setFunctionalClass(parseInt(functionalClassSelection, 10));
          }

          var trafficDirectionSelection = $('#traffic-direction-selection').val();
          if (!_.isEmpty(trafficDirectionSelection)) {
            selectedLinkProperty.setTrafficDirection(trafficDirectionSelection);
          }

          var linkTypeSelection = $('#link-type-selection').val();
          if (!_.isEmpty(linkTypeSelection)) {
            selectedLinkProperty.setLinkType(parseInt(linkTypeSelection, 10));
          }

          selectedLinkProperty.save();
          purge();
        });
      };

      var show = function() {
        renderDialog();
        bindEvents();
      };

      var purge = function() {
        $('.mass-update-modal').remove();
      };
      show();
    };

    var massUpdateHandler = new LinearAssetMassUpdate(map, roadLayer.layer, selectedLinkProperty, showMassUpdateDialog);

    this.activateSelection = function() {
      updateMassUpdateHandlerState();
      doubleClickSelectControl.activate();
    };
    this.deactivateSelection = function() {
      updateMassUpdateHandlerState();
      doubleClickSelectControl.deactivate();
    };

    var updateMassUpdateHandlerState = function() {
      if (!applicationModel.isReadOnly() &&
          applicationModel.getSelectedTool() === 'Select' &&
          applicationModel.getSelectedLayer() === layerName) {
        massUpdateHandler.activate();
      } else {
        massUpdateHandler.deactivate();
      }
    };

    var highlightFeatures = function() {
      _.each(roadLayer.layer.features, function(x) {
        if (selectedLinkProperty.isSelected(x.attributes.mmlId)) {
          selectControl.highlight(x);
        } else {
          selectControl.unhighlight(x);
        }
      });
    };

    var draw = function() {
      prepareRoadLinkDraw();
      var roadLinks = roadCollection.getAll();
      roadLayer.drawRoadLinks(roadLinks, map.getZoom());
      drawDashedLineFeaturesIfApplicable(roadLinks);
      me.drawOneWaySigns(roadLayer.layer, roadLinks, geometryUtils);
      redrawSelected();
      eventbus.trigger('linkProperties:available');
    };

    this.refreshView = function() {
      eventbus.once('roadLinks:fetched', function() { draw(); });
      roadCollection.fetchFromVVH(map.getExtent());
    };

    this.isDirty = function() {
      return selectedLinkProperty.isDirty();
    };

    var createDashedLineFeatures = function(roadLinks, dashedLineFeature) {
      return _.flatten(_.map(roadLinks, function(roadLink) {
        var points = _.map(roadLink.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        var attributes = {
          dashedLineFeature: roadLink[dashedLineFeature],
          mmlId: roadLink.mmlId,
          type: 'overlay',
          linkType: roadLink.linkType
        };
        return new OpenLayers.Feature.Vector(new OpenLayers.Geometry.LineString(points), attributes);
      }));
    };

    var drawDashedLineFeatures = function(roadLinks) {
      var dashedFunctionalClasses = [2, 4, 6, 8];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedFunctionalClasses, roadLink.functionalClass);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'functionalClass'));
    };

    var drawDashedLineFeaturesForType = function(roadLinks) {
      var dashedLinkTypes = [2, 4, 6, 8, 12, 21];
      var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
        return _.contains(dashedLinkTypes, roadLink.linkType);
      });
      roadLayer.layer.addFeatures(createDashedLineFeatures(dashedRoadLinks, 'linkType'));
    };

    var getSelectedFeatures = function() {
      return _.filter(roadLayer.layer.features, function (feature) {
        return selectedLinkProperty.isSelected(feature.attributes.mmlId);
      });
    };

    var reselectRoadLink = function() {
      me.activateSelection();
      var originalOnSelectHandler = selectControl.onSelect;
      selectControl.onSelect = function() {};
      var features = getSelectedFeatures();
      if (!_.isEmpty(features)) {
        currentRenderIntent = 'select';
        selectControl.select(_.first(features));
        highlightFeatures();
      }
      selectControl.onSelect = originalOnSelectHandler;
      if (selectedLinkProperty.isDirty()) {
        me.deactivateSelection();
      }
    };

    var prepareRoadLinkDraw = function() {
      me.deactivateSelection();
    };

    var drawDashedLineFeaturesIfApplicable = function(roadLinks) {
      if (linkPropertiesModel.getDataset() === 'functional-class') {
        drawDashedLineFeatures(roadLinks);
      } else if (linkPropertiesModel.getDataset() === 'link-type') {
        drawDashedLineFeaturesForType(roadLinks);
      }
    };

    this.layerStarted = function(eventListener) {
      var linkPropertyChangeHandler = _.partial(handleLinkPropertyChanged, eventListener);
      var linkPropertyEditConclusion = _.partial(concludeLinkPropertyEdit, eventListener);
      eventListener.listenTo(eventbus, 'linkProperties:changed', linkPropertyChangeHandler);
      eventListener.listenTo(eventbus, 'linkProperties:cancelled linkProperties:saved', linkPropertyEditConclusion);
      eventListener.listenTo(eventbus, 'linkProperties:saved', refreshViewAfterSaving);
      eventListener.listenTo(eventbus, 'linkProperties:selected linkProperties:multiSelected', function(link) {
        var feature = _.find(roadLayer.layer.features, function(feature) {
          return feature.attributes.mmlId === link.mmlId;
        });
        if (feature) {
          selectControl.select(feature);
        }
      });
      eventListener.listenTo(eventbus, 'linkProperties:dataset:changed', function() {
        draw();
      });
      eventListener.listenTo(eventbus, 'application:readOnly', updateMassUpdateHandlerState);
    };

    var refreshViewAfterSaving = function() {
      unselectRoadLink();
      me.refreshView();
    };

    var handleLinkPropertyChanged = function(eventListener) {
      redrawSelected();
      me.deactivateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      eventListener.listenTo(eventbus, 'map:clicked', me.displayConfirmMessage);
    };

    var concludeLinkPropertyEdit = function(eventListener) {
      me.activateSelection();
      eventListener.stopListening(eventbus, 'map:clicked', me.displayConfirmMessage);
      redrawSelected();
    };

    var redrawSelected = function() {
      roadLayer.layer.removeFeatures(getSelectedFeatures());
      var selectedRoadLinks = selectedLinkProperty.get();
      _.each(selectedRoadLinks,  function(selectedLink) { roadLayer.drawRoadLink(selectedLink); });
      drawDashedLineFeaturesIfApplicable(selectedRoadLinks);
      me.drawOneWaySigns(roadLayer.layer, selectedRoadLinks, geometryUtils);
      reselectRoadLink();
    };

    this.removeLayerFeatures = function() {
      roadLayer.layer.removeFeatures(roadLayer.layer.getFeaturesByAttribute('type', 'overlay'));
    };

    var show = function(map) {
      if (zoomlevels.isInRoadLinkZoomLevel(map.getZoom())) {
        me.start();
      }
    };

    var hideLayer = function() {
      unselectRoadLink();
      me.stop();
      me.hide();
    };

    return {
      show: show,
      hide: hideLayer,
      minZoomForContent: me.minZoomForContent
    };
  };
})(this);
