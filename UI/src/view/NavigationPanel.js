(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, searchBox, layerSelectBox, assetElementGroups) {
    var navigationPanel = $('<div class="navigation-panel"></div>');

    navigationPanel.append(searchBox.element);
    navigationPanel.append(layerSelectBox.element);

    var assetElements = _.flatten(assetElementGroups);

    var assetElementDiv = $('<div></div>');
    assetElements.forEach(function(asset) {
      assetElementDiv.append(asset.element.domElement);
    });
    navigationPanel.append(assetElementDiv);

    var assetControls = _.chain(assetElements)
      .map(function(asset) {
        return [asset.layerName, asset.element];
      })
      .zipObject()
      .value();

    function bindEvents() {
      layerSelectBox.button.on('click', function() {
        assetElementDiv.toggle();
        layerSelectBox.toggle();
      });

      $(document).on('click', function(evt) {
        var clickOutside = !$(evt.target).closest('.navigation-panel').length;
        if (clickOutside) {
          layerSelectBox.hide();
          assetElementDiv.show();
        }
      });

      $(document).keyup(function(evt) {
        if (evt.keyCode === 27) {
          layerSelectBox.hide();
          assetElementDiv.show();
        }
      });

    }

    bindEvents();

    eventbus.on('layer:selected', function selectLayer(layer, previouslySelectedLayer) {
      var previousControl = assetControls[previouslySelectedLayer];
      if (previousControl) previousControl.hide();
      assetControls[layer].show();
      assetElementDiv.show();
    });

    container.append(navigationPanel);
  }
})(this);
