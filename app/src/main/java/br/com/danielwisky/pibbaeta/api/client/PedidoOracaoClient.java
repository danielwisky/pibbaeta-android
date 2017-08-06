package br.com.danielwisky.pibbaeta.api.client;

import br.com.danielwisky.pibbaeta.api.dto.PedidoOracaoDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PedidoOracaoClient {

  @POST("v1/pedido-oracao")
  Call<Void> enviar(@Body PedidoOracaoDto pedidoOracao);
}