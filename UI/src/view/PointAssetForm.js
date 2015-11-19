(function (root) {
  root.PointAssetForm = {
    initialize: bindEvents
  };

  function bindEvents(selectedAsset) {
    var rootElement = $('#feature-attributes');

    function toggleMode(readOnly) {
      rootElement.find('.delete').toggle(!readOnly);
      rootElement.find('.form-controls').toggle(!readOnly);
    }
    eventbus.on('application:readOnly', toggleMode);

    eventbus.on('pedestrianCrossing:selected pedestrianCrossing:cancelled', function() {
      renderForm(rootElement, selectedAsset);
      toggleMode(applicationModel.isReadOnly());
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on('pedestrianCrossing:changed', function() {
      rootElement.find('.form-controls button').attr('disabled', !selectedAsset.isDirty());
    });

    eventbus.on('pedestrianCrossing:unselected', function() {
      rootElement.empty();
    });

    eventbus.on('layer:selected', function(layer) {
      if(layer === 'pedestrianCrossing') {
        renderLinktoWorkList();
      }
      else {
        $('#point-asset-work-list-link').parent().remove();
      }
    });
  }

  function enableSaving(rootElement) {
    rootElement.find('.save.btn').prop('disabled', false);
  }

  function renderForm(rootElement, selectedAsset) {
    var id = selectedAsset.getId();

    var title = selectedAsset.isNew() ? "Uusi suojatie" : 'ID: ' + id;
    var header = '<header><span>' + title + '</span>' + renderButtons() + '</header>';
    var form = renderMeta(selectedAsset);
    var footer = '<footer>' + renderButtons() + '</footer>';

    rootElement.html(header + form + footer);

    rootElement.find('input[type="checkbox"]').on('change', function(event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.setToBeRemoved(eventTarget.attr('checked') === 'checked');
    });

    rootElement.find('.pointasset button.save').on('click', function() {
      selectedAsset.save();
    });

    rootElement.find('.pointasset button.cancel').on('click', function() {
      selectedAsset.cancel();
    });
  }

  function renderMeta(selectedAsset) {
    var asset = selectedAsset.asset();
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
      '    <div class="form-group form-group delete">' +
      '      <div class="checkbox" >' +
      '        <input type="checkbox">' +
      '      </div>' +
      '      <p class="form-control-static">Poista</p>' +
      '    </div>' +
      '  </div>' +
      '</div>';
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
    var notRendered = !$('#point-asset-work-list-link').length;
    if(notRendered) {
      $('#information-content').append('' +
        '<div class="form form-horizontal">' +
        '<a id="point-asset-work-list-link" class="floating-pedestrian-crossings" href="#work-list/pedestrianCrossing">Geometrian ulkopuolelle jääneet suojatiet</a>' +
        '</div>');
    }
  }
})(this);
