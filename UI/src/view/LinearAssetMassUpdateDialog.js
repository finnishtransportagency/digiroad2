(function(root) {
  root.LinearAssetMassUpdateDialog = {
    show: init
  };

  function init(options) {
    var count = options.count,
      onCancel = options.onCancel,
      onSave = options.onSave;

    var confirmDiv =
      '<div class="modal-overlay mass-update-modal">' +
      '<div class="modal-dialog">' +
      '<div class="content">' +
      'Olet valinnut <%- count %> linkkiä' +
      '</div>' +
      options.formElements.singleValueElement(options.selectedAsset) +
      '<div class="actions">' +
      '<button class="btn btn-primary save">Tallenna</button>' +
      '<button class="btn btn-secondary close">Peruuta</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '<div id="hid" style="visibility: hidden;"></div>div>';

    var renderDialog = function() {
      var container = $('.container').append(_.template(confirmDiv)({
        count: count
      }));
      options.formElements.bindMassUpdateDialog(container, $('#hid'));
    };

    var bindEvents = function() {
      $('.mass-update-modal .close').on('click', function() {
        purge();
        onCancel();
      });

      $('.mass-update-modal .save').on('click', function() {
        $('.modal-dialog').find('.actions button').attr('disabled', true);

        var newValue = parseInt($('#hid').text(), 10);

        purge();

        onSave(newValue);
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