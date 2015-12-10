(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset, layerName) {
    var rootElement = $('#feature-attributes');

    function toggleMode(readOnly) {
      rootElement.find('.delete').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
      rootElement.find('.editable .form-control-static').toggle(readOnly);
      rootElement.find('.editable .form-control').toggle(!readOnly);
    }

    eventbus.on('application:readOnly', toggleMode);

    eventbus.on(layerName + ':selected ' + layerName + ':cancelled', function() {
      renderForm(rootElement, selectedAsset);
      toggleMode(applicationModel.isReadOnly());
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on(layerName + ':changed', function() {
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
      rootElement.empty();
    });

    eventbus.on('layer:selected', function(layer) {
      if(layer === 'pedestrianCrossings') {
        renderLinktoWorkList();
      }
      else {
        $('#point-asset-work-list-link').parent().remove();
      }
    });
  }

  function renderForm(rootElement, selectedAsset) {
    var id = selectedAsset.getId();

    var title = selectedAsset.isNew() ? "Uusi suojatie" : 'ID: ' + id;
    var header = '<header><span>' + title + '</span>' + renderButtons() + '</header>';
    var form = renderAssetFormElements(selectedAsset);
    var footer = '<footer>' + renderButtons() + '</footer>';

    rootElement.html(header + form + footer);

    rootElement.find('input[type="checkbox"]').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.setToBeRemoved(eventTarget.attr('checked') === 'checked');
    });

    rootElement.find('select').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({ obstacletype: parseInt(eventTarget.val(), 10) });
    });

    rootElement.find('.pointasset button.save').on('click', function() {
      selectedAsset.save();
    });

    rootElement.find('.pointasset button.cancel').on('click', function() {
      selectedAsset.cancel();
    });
  }

  function renderAssetFormElements(selectedAsset) {
    var asset = selectedAsset.get();
    if (selectedAsset.isNew()) return '';

    return '' +
      '<div class="wrapper">' +
      '  <div class="form form-horizontal form-dark form-pointasset">' +
          renderFloatingNotification(asset.floating) +
      '    <div class="form-group">' +
      '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
      '    </div>' +
      '    <div class="form-group">' +
      '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
      '    </div>' +
           renderValueElement(asset) +
      '    <div class="form-group form-group delete">' +
      '      <div class="checkbox" >' +
      '        <input type="checkbox">' +
      '      </div>' +
      '      <p class="form-control-static">Poista</p>' +
      '    </div>' +
      '  </div>' +
      '</div>';
  }

  function renderValueElement(asset) {
    var obstacleTypes = {
      1: 'Suljettu yhteys',
      2: 'Avattava puomi'
    };
    if (asset.obstacleType) {
      return '' +
        '    <div class="form-group editable">' +
        '      <label class="control-label">' + 'Esterakennelma' + '</label>' +
        '      <p class="form-control-static">' + obstacleTypes[asset.obstacleType] + '</p>' +
        '      <select class="form-control" style="display:none">  ' +
        '        <option value="1">Suljettu yhteys</option>' +
        '        <option value="2">Avattava puomi</option>' +
        '      </select>' +
        '    </div>';
    } else {
      return '';
    }
  }

  function renderButtons() {
    return '' +
      '<div class="pointasset form-controls">' +
      '  <button class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
  }

  function renderFloatingNotification(floating) {
    if (floating) {
      return '' +
        '<div class="form-group form-notification">' +
        ' <p>Kadun tai tien geometria on muuttunut, tarkista ja korjaa suojatien sijainti</p>' +
        '</div>';
    } else {
      return '';
    }
  }

  function renderLinktoWorkList() {
    $('#information-content').append('' +
      '<div class="form form-horizontal">' +
      '<a id="point-asset-work-list-link" class="floating-pedestrian-crossings" href="#work-list/pedestrianCrossings">Geometrian ulkopuolelle jääneet suojatiet</a>' +
      '</div>');
  }
})(this);
