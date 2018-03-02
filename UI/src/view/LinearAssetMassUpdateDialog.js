(function(root) {
  root.LinearAssetMassUpdateDialog = {
    show: init
  };

  function init(options) {
    var count = options.count,
      onCancel = options.onCancel,
      onSave = options.onSave,
      validator = options.validator,
      formElements = options.formElements,
      selectedLinearAsset = options.selectedLinearAsset,
      assetTypeConfiguration = options.assetTypeConfiguration,
      currentValue;

    var confirmDiv =
      '<div class="modal-overlay mass-update-modal">' +
        '<div class="modal-dialog form form-horizontal linear-asset">' +
          '<div class="content">' +
            'Olet valinnut <%- count %> tielinkkiä' +
          '</div>' +
          '<div class="form-elements-container">' +
            '<%= editElement %>'+
          '</div>'+
          '<div class="actions">' +
            '<button class="btn btn-primary save">Tallenna</button>' +
            '<button class="btn btn-secondary close">Peruuta</button>' +
          '</div>' +
        '</div>' +
      '</div>';

    function setValue(value) {
      if (validator(value)) {
        currentValue = value;
        $('button.save').prop('disabled', '');
      } else {
        $('button.save').prop('disabled', 'disabled');
      }
    }

    function _setValue(value){
      if (validator(value)) {

        if(!currentValue)
          currentValue  = { properties: [] };

        currentValue.properties.push({
            publicId: value.properties[0].publicId,
            value: value.properties[0].value
        });
        $('button.save').prop('disabled', '');
      } else {
        $('button.save').prop('disabled', 'disabled');
      }
    }


    function removeValue() {
      currentValue = undefined;
      $('button.save').prop('disabled', '');
    }

    var renderDialog = function() {
      var container = $('.container').append(_.template(confirmDiv)({
        count: count,
        editElement: formElements.singleValueElement(undefined)
      }));
      formElements.bindEvents(container.find('.mass-update-modal .form-elements-container'), {
        setValue: setValue,
        removeValue: removeValue
      });
    };

    var _renderDialog = function() {
      var container = $('.container').append(_.template(confirmDiv)({ count: count,  editElement: '' }));
      selectedLinearAsset.setValue =  _setValue;
      selectedLinearAsset.removeValue = removeValue;
      container.find('.form-elements-container').html(formElements.renderForm(selectedLinearAsset).find('.editable'));
      formElements.bindEvents(container.find('.mass-update-modal .form-elements-container'), assetTypeConfiguration);
    };


    var bindEvents = function() {
      $('.mass-update-modal .close').on('click', function() {
        purge();
        onCancel();
      });

      $('.mass-update-modal .save').on('click', function() {
        purge();
        onSave(currentValue);
      });
    };

    var show = function() {
      purge();
      if(assetTypeConfiguration.formElements.singleValueElement)
        renderDialog();
      else
        _renderDialog();
      bindEvents();
    };

    var purge = function() {
      $('.mass-update-modal').remove();
    };

    show();
  }
})(this);
