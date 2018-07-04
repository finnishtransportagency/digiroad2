(function(root) {
  root.SpeedLimitMassUpdateDialog = {
    show: init
  };

  function init(options) {
    var count = options.count,
      onCancel = options.onCancel,
      onSave = options.onSave,
      setMassValue = options.setMassValue;

    var SPEED_LIMITS = [120, 100, 90, 80, 70, 60, 50, 40, 30, 20];
    var defaultUnknownOptionTag = ['<option value="" style="display:none;"></option>'];
    var speedLimitOptionTags = defaultUnknownOptionTag.concat(_.map(SPEED_LIMITS, function(value) {
      return '<option value="' + value + '">' + value + '</option>';
    }));
    var confirmDiv =
      '<div class="modal-overlay mass-update-modal">' +
      '<div class="modal-dialog">' +
      '<div class="content">' +
      'Olet valinnut <%- count %> tielinkkiä' +
      '</div>' +
      '<div class="form-group editable">' +
      '<label class="control-label">Nopeusrajoitus</label>' +
      '<select class="form-control">' + speedLimitOptionTags.join('') + '</select>' +
      '</div>' +
      '<div class="actions">' +
      '<button class="btn btn-primary save" disabled="disabled">Tallenna</button>' +
      '<button class="btn btn-secondary close">Peruuta</button>' +
      '</div>' +
      '</div>' +
      '</div>';

    var renderDialog = function() {
      $('.container').append(_.template(confirmDiv)({
        count: count
      }));
    };

    var bindEvents = function() {
      $('.mass-update-modal .close').on('click', function() {
        purge();
        onCancel();
      });

      $('.mass-update-modal .save').on('click', function() {
        $('.modal-dialog').find('.actions button').attr('disabled', true);

        var newSpeedLimit = parseInt($('.mass-update-modal select').val(), 10);

        purge();

        onSave(newSpeedLimit);
      });

      $('.mass-update-modal select').on('change', function () {
        var value = $(this).val();
        $('.save').attr('disabled', !value);
        if(value)
          setMassValue(parseInt(value));
      });
    };

    var show = function() {
      purge();
      renderDialog();
      bindEvents();
    };

    var purge = function() {
      $('.mass-update-modal').remove();
    };

    show();
  }
})(this);