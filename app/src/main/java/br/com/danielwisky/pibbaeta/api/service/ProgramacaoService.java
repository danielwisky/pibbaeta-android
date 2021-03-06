package br.com.danielwisky.pibbaeta.api.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import br.com.danielwisky.pibbaeta.api.client.WebClient;
import br.com.danielwisky.pibbaeta.api.resources.response.AgendaResponse;
import br.com.danielwisky.pibbaeta.api.resources.response.ProgramacaoResponse;
import br.com.danielwisky.pibbaeta.dao.DaoSession;
import br.com.danielwisky.pibbaeta.dao.Programacao;
import br.com.danielwisky.pibbaeta.dao.ProgramacaoDao;
import br.com.danielwisky.pibbaeta.dao.ProgramacaoDao.Properties;
import br.com.danielwisky.pibbaeta.event.ProgramacaoEvent;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.greenrobot.eventbus.EventBus;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProgramacaoService {

  private static final String TAG = ProgramacaoService.class.getSimpleName();
  private static final String PREFERENCES = ProgramacaoService.class.getName();

  private static final String VERSAO_AGENDA = "VERSAO_AGENDA";
  private static final String ATIVO = "ATIVO";
  private static final String EMPTY = "";

  private final Context context;
  private final ProgramacaoDao programacaoDao;

  public ProgramacaoService(Context context, DaoSession daoSession) {
    this.context = context;
    this.programacaoDao = daoSession.getProgramacaoDao();
  }

  public void sincronizar(AgendaResponse agendaResponse) {
    if (validaVersao(agendaResponse.getDataAtualizacao())) {
      salvar(agendaResponse);
    }
  }

  public void sincronizar() {
    Call<AgendaResponse> call = temVersao() ?
        new WebClient().getProgramacaoClient().listar(getVersao()) :
        new WebClient().getProgramacaoClient().listar();
    call.enqueue(buscaProgramacaoCallback());
  }

  private Callback<AgendaResponse> buscaProgramacaoCallback() {
    return new Callback<AgendaResponse>() {
      @Override
      public void onResponse(Call<AgendaResponse> call, Response<AgendaResponse> response) {
        final AgendaResponse agendaResponse = response.body();
        if (agendaResponse != null) {
          salvar(agendaResponse);
        }
      }

      @Override
      public void onFailure(Call<AgendaResponse> call, Throwable t) {
        Log.e(TAG, t.getMessage());
      }
    };
  }

  private void salvar(AgendaResponse agendaResponse) {
    final List<ProgramacaoResponse> programacoes = agendaResponse.getProgramacoes();
    salvar(programacoes);
    setVersao(agendaResponse.getDataAtualizacao());
    EventBus.getDefault().post(new ProgramacaoEvent());
  }

  private void salvar(List<ProgramacaoResponse> programacoes) {
    for (ProgramacaoResponse response : programacoes) {

      final Programacao programacao =
          programacaoDao.queryBuilder().where(Properties.IdExterno.eq(response.getId())).unique();

      if (programacao != null) {
        if (ATIVO.equals(response.getStatus())) {
          final Programacao model = response.toModel();
          model.setId(programacao.getId());
          programacaoDao.update(model);
        } else {
          programacaoDao.deleteByKey(programacao.getId());
        }
      } else if (ATIVO.equals(response.getStatus())) {
        programacaoDao.insert(response.toModel());
      }
    }
  }

  private void setVersao(final String versao) {
    final SharedPreferences preferences = getSharedPreferences();
    final SharedPreferences.Editor editor = preferences.edit();
    editor.putString(VERSAO_AGENDA, versao);
    editor.commit();
  }

  private String getVersao() {
    final SharedPreferences preferences = getSharedPreferences();
    return preferences.getString(VERSAO_AGENDA, EMPTY);
  }

  private boolean temVersao() {
    return !getVersao().isEmpty();
  }

  private boolean validaVersao(String versao) {

    if (!temVersao()) {
      return true;
    }

    final Locale locale = new Locale("pt", "BR");
    final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", locale);

    try {
      final Date dataExterna = format.parse(versao);
      final Date dataInterna = format.parse(getVersao());
      return dataExterna.after(dataInterna);
    } catch (ParseException e) {
      Log.e(TAG, e.getMessage());
    }

    return false;
  }

  private SharedPreferences getSharedPreferences() {
    return this.context.getSharedPreferences(PREFERENCES, this.context.MODE_PRIVATE);
  }
}