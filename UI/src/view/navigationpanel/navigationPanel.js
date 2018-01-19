(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, searchBox, layerSelectBox, assetControlGroups) {
    var navigationPanel = $('<div class="navigation-panel"></div>');

    navigationPanel.append(searchBox.element);
    navigationPanel.append(layerSelectBox.element);

    var assetControls = _.flatten(assetControlGroups);

    var assetElementDiv = $('<div></div>');
    assetControls.forEach(function(asset) {
      assetElementDiv.append(asset.element);
    });
    navigationPanel.append(assetElementDiv);

    var assetControlMap = _.chain(assetControls)
      .map(function(asset) {
        return [asset.layerName, asset];
      })
      .zipObject()
      .value();

    bindEvents();

    eventbus.on('layer:selected', function selectLayer(layer, previouslySelectedLayer) {
      var previousControl = assetControlMap[previouslySelectedLayer];
      if (previousControl) previousControl.hide();
      assetControlMap[layer].show();
      assetElementDiv.show();
    });

    container.append(navigationPanel);
    
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
  }
})(this);
