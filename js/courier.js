$(function() {
  $('#right-nav').affix({
      offset: {
          top: $('#right-nav').offset().top
      }
  });
  anchors.options = {
    placement: 'left'
  };
  anchors.add('h1');
  anchors.add('h2');
  anchors.add('h3');
  anchors.add('h4');
  anchors.add('h5');
  anchors.add('h6');

  var changeFileFormat = function(extension) {
    $('.' + extension + '-fileformat-tab').each(function() {
      $(this).tab('show');
    });
  };

  $('.fileformat-tab').each(function() {
    $(this).click(function (e) {
      e.preventDefault();
      var format = $(this).data('format');
      changeFileFormat(format);
    });
  });
});
